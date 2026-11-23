package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageDiagnosticsOrderVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageOrderVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.ResponseVM;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orders an item from a patient package in a visit. The order is made with the normal order services
 * (so it follows the usual workflow), and the quantity is taken from the package in the same transaction:
 * if the order fails nothing is taken.
 */
@Service
@Transactional
public class PatientPackageOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPackageOrderService.class);
    private static final String ENTITY = "patientPackage";

    private final PatientPackageRepository packageRepository;
    private final PatientPackageItemRepository itemRepository;
    private final PatientPackageUsageRepository usageRepository;
    private final PatientEncounterRepository encounterRepository;
    private final DiagnosticOrderService diagnosticOrderService;
    private final DiagnosticOrderTestService diagnosticOrderTestService;
    private final PatientProcedureService patientProcedureService;
    private final PatientServiceAndProductService patientServiceAndProductService;
    private final EntityManager entityManager;

    public PatientPackageOrderService(
            PatientPackageRepository packageRepository,
            PatientPackageItemRepository itemRepository,
            PatientPackageUsageRepository usageRepository,
            PatientEncounterRepository encounterRepository,
            DiagnosticOrderService diagnosticOrderService,
            DiagnosticOrderTestService diagnosticOrderTestService,
            PatientProcedureService patientProcedureService,
            PatientServiceAndProductService patientServiceAndProductService,
            EntityManager entityManager
    ) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.usageRepository = usageRepository;
        this.encounterRepository = encounterRepository;
        this.diagnosticOrderService = diagnosticOrderService;
        this.diagnosticOrderTestService = diagnosticOrderTestService;
        this.patientProcedureService = patientProcedureService;
        this.patientServiceAndProductService = patientServiceAndProductService;
        this.entityManager = entityManager;
    }

    public record OrderResultVM(
            Long usageId,
            String orderSource,
            Long orderId,
            ResponseVM patientPackage
    ) implements Serializable {}

    public OrderResultVM order(Long itemId, PatientPackageOrderVM vm) {
        PatientPackageItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new NotFoundAlertException("Package item not found with id " + itemId, ENTITY, "item.notfound"));
        PatientPackage pkg = packageRepository.findById(item.getPatientPackageId())
                .orElseThrow(() -> new NotFoundAlertException("Patient package not found", ENTITY, "notfound"));
        if (pkg.getStatus() != PatientPackageStatus.ACTIVE) {
            throw new BadRequestAlertException("This package is " + pkg.getStatus().name().toLowerCase(), ENTITY, "package.notActive");
        }

        PatientEncounter encounter = encounterRepository.findById(vm.encounterId())
                .orElseThrow(() -> new NotFoundAlertException("Encounter not found with id " + vm.encounterId(), ENTITY, "encounter.notfound"));
        Long encounterPatientId = encounter.getPatient() != null ? encounter.getPatient().getId() : null;
        if (!pkg.getPatientId().equals(encounterPatientId)) {
            throw new BadRequestAlertException("The encounter does not belong to the package's patient", ENTITY, "encounter.patient.mismatch");
        }

        int quantity = vm.quantity() == null ? 1 : vm.quantity();
        BillingItemTypes type = item.getItemType();
        boolean oneAtATime = type != BillingItemTypes.SERVICE && type != BillingItemTypes.MEDICATION;
        if (quantity < 1 || (oneAtATime && quantity != 1)) {
            throw new BadRequestAlertException(
                    oneAtATime ? "This item is ordered one at a time" : "Quantity must be at least 1", ENTITY, "item.quantity");
        }
        if (quantity > item.getQuantityRemaining()) {
            throw new BadRequestAlertException(
                    "Only " + item.getQuantityRemaining() + " left of " + item.getItemName(), ENTITY, "item.notEnough");
        }

        // The usage row is written first: a service line points back to it through source_id.
        PatientPackageUsage usage = usageRepository.saveAndFlush(PatientPackageUsage.builder()
                .patientPackageId(pkg.getId())
                .patientPackageItemId(item.getId())
                .encounterId(encounter.getId())
                .quantity(quantity)
                .status(PatientPackageUsageStatus.USED)
                .build());

        String orderSource;
        Long orderId;
        switch (type) {
            case LABORATORY, RADIOLOGY, PATHOLOGY -> {
                orderSource = type.name();
                orderId = orderDiagnosticTest(pkg, item, encounter, vm);
            }
            case PROCEDURE -> {
                orderSource = PatientPackageUsageReversalService.SOURCE_PROCEDURE;
                orderId = orderProcedure(pkg, item, encounter, vm);
            }
            case SERVICE -> {
                orderSource = PatientPackageUsageReversalService.SOURCE_SERVICE;
                orderId = orderService(pkg, item, encounter, usage, quantity, vm);
            }
            default -> throw new BadRequestAlertException(
                    "Ordering " + type.name().toLowerCase() + " items from a package is not available yet", ENTITY, "item.typeNotSupported");
        }

        usage.setOrderSource(orderSource);
        usage.setOrderId(orderId);
        usageRepository.save(usage);

        item.setQuantityUsed((item.getQuantityUsed() == null ? 0 : item.getQuantityUsed()) + quantity);
        itemRepository.saveAndFlush(item);

        List<PatientPackageItem> items = itemRepository.findByPatientPackageIdOrderByIdAsc(pkg.getId());
        if (items.stream().allMatch(i -> i.getQuantityRemaining() == 0)) {
            pkg.setStatus(PatientPackageStatus.COMPLETED);
            packageRepository.save(pkg);
        }

        LOG.info("Package {} item {} ({}) ordered in encounter {}: {} id={} qty={}",
                pkg.getId(), item.getId(), item.getItemName(), encounter.getId(), orderSource, orderId, quantity);
        return new OrderResultVM(usage.getId(), orderSource, orderId, ResponseVM.ofEntity(pkg, items));
    }

    public record DiagnosticsOrderResultVM(
            Long diagnosticOrderId,
            List<Long> diagnosticOrderTestIds,
            List<ResponseVM> patientPackages
    ) implements Serializable {}

    /**
     * Several package tests in ONE diagnostic order, submitted once. Each test takes one from its package item.
     * All or nothing: if any test fails, nothing is ordered and nothing is taken.
     */
    public DiagnosticsOrderResultVM orderDiagnostics(PatientPackageDiagnosticsOrderVM vm) {
        PatientEncounter encounter = encounterRepository.findById(vm.encounterId())
                .orElseThrow(() -> new NotFoundAlertException("Encounter not found with id " + vm.encounterId(), ENTITY, "encounter.notfound"));
        Long patientId = encounter.getPatient() != null ? encounter.getPatient().getId() : null;

        // Lock in id order so two users ordering overlapping items cannot deadlock.
        List<Long> itemIds = vm.items().stream().map(PatientPackageDiagnosticsOrderVM.Line::itemId).distinct().sorted().toList();
        if (itemIds.size() != vm.items().size()) {
            throw new BadRequestAlertException("The same item is selected twice", ENTITY, "item.duplicate");
        }
        Map<Long, PatientPackageItem> itemsById = new LinkedHashMap<>();
        Map<Long, PatientPackage> packagesById = new HashMap<>();
        Set<Long> testIds = new HashSet<>();
        for (Long id : itemIds) {
            PatientPackageItem item = itemRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundAlertException("Package item not found with id " + id, ENTITY, "item.notfound"));
            if (!PatientPackageUsageReversalService.DIAGNOSTIC_SOURCES.contains(item.getItemType().name())) {
                throw new BadRequestAlertException(item.getItemName() + " is not a diagnostic test", ENTITY, "item.notDiagnostic");
            }
            PatientPackage pkg = packagesById.computeIfAbsent(item.getPatientPackageId(), pid -> packageRepository.findById(pid)
                    .orElseThrow(() -> new NotFoundAlertException("Patient package not found", ENTITY, "notfound")));
            if (pkg.getStatus() != PatientPackageStatus.ACTIVE) {
                throw new BadRequestAlertException("Package " + pkg.getPackageName() + " is not active", ENTITY, "package.notActive");
            }
            if (!pkg.getPatientId().equals(patientId)) {
                throw new BadRequestAlertException("The encounter does not belong to the package's patient", ENTITY, "encounter.patient.mismatch");
            }
            if (item.getQuantityRemaining() < 1) {
                throw new BadRequestAlertException("Nothing left of " + item.getItemName(), ENTITY, "item.notEnough");
            }
            if (!testIds.add(item.getSourceId())) {
                throw new BadRequestAlertException(item.getItemName() + " is selected twice (from two packages)", ENTITY, "item.duplicateTest");
            }
            itemsById.put(id, item);
        }

        DiagnosticOrder order = diagnosticOrderService.create(new DiagnosticOrderCreateDTO(
                patientId,
                encounter.getId(),
                Boolean.TRUE.equals(vm.isUrgent()),
                null,
                null,
                encounter.getDepartmentId(),
                encounter.getFacilityId()
        ));

        List<Long> createdTestIds = new ArrayList<>();
        for (PatientPackageDiagnosticsOrderVM.Line line : vm.items()) {
            PatientPackageItem item = itemsById.get(line.itemId());
            DiagnosticOrderTest test = diagnosticOrderTestService.create(new DiagnosticOrderTestCreateDTO(
                    order.getId(),
                    item.getSourceId(),
                    null,
                    line.reason(),
                    vm.notes(),
                    TestType.valueOf(item.getItemType().name()),
                    vm.icdDiagnosisId(),
                    vm.acceptUncoveredAsCash()
            ));
            createdTestIds.add(test.getId());

            usageRepository.save(PatientPackageUsage.builder()
                    .patientPackageId(item.getPatientPackageId())
                    .patientPackageItemId(item.getId())
                    .encounterId(encounter.getId())
                    .quantity(1)
                    .orderSource(item.getItemType().name())
                    .orderId(test.getId())
                    .status(PatientPackageUsageStatus.USED)
                    .build());
            item.setQuantityUsed((item.getQuantityUsed() == null ? 0 : item.getQuantityUsed()) + 1);
            itemRepository.save(item);
        }
        itemRepository.flush();

        diagnosticOrderService.submit(order.getId(), SecurityUtils.getCurrentUserLogin().orElse("system"));

        List<ResponseVM> packages = new ArrayList<>();
        for (PatientPackage pkg : packagesById.values()) {
            List<PatientPackageItem> items = itemRepository.findByPatientPackageIdOrderByIdAsc(pkg.getId());
            if (items.stream().allMatch(i -> i.getQuantityRemaining() == 0)) {
                pkg.setStatus(PatientPackageStatus.COMPLETED);
                packageRepository.save(pkg);
            }
            packages.add(ResponseVM.ofEntity(pkg, items));
        }

        LOG.info("Package tests ordered in encounter {}: diagnostic order {} with {} tests",
                encounter.getId(), order.getId(), createdTestIds.size());
        return new DiagnosticsOrderResultVM(order.getId(), createdTestIds, packages);
    }

    /** New diagnostic order with the one test, submitted right away (the same as Submit on the order screen). */
    private Long orderDiagnosticTest(PatientPackage pkg, PatientPackageItem item, PatientEncounter encounter, PatientPackageOrderVM vm) {
        DiagnosticOrder order = diagnosticOrderService.create(new DiagnosticOrderCreateDTO(
                pkg.getPatientId(),
                encounter.getId(),
                Boolean.TRUE.equals(vm.isUrgent()),
                null,
                null,
                encounter.getDepartmentId(),
                encounter.getFacilityId()
        ));
        DiagnosticOrderTest test = diagnosticOrderTestService.create(new DiagnosticOrderTestCreateDTO(
                order.getId(),
                item.getSourceId(),
                null,
                vm.reason(),
                vm.notes(),
                TestType.valueOf(item.getItemType().name()),
                vm.icdDiagnosisId(),
                vm.acceptUncoveredAsCash()
        ));
        String username = SecurityUtils.getCurrentUserLogin().orElse("system");
        diagnosticOrderService.submit(order.getId(), username);
        return test.getId();
    }

    private Long orderProcedure(PatientPackage pkg, PatientPackageItem item, PatientEncounter encounter, PatientPackageOrderVM vm) {
        if (vm.procedureLevel() == null || vm.priority() == null
                || vm.bodyPart() == null || vm.bodyPart().isBlank() || vm.scheduledDateTime() == null) {
            throw new BadRequestAlertException(
                    "Procedure level, priority, body part and schedule are required", ENTITY, "procedure.details.required");
        }
        PatientProcedure procedure = patientProcedureService.create(new PatientProcedureCreateDTO(
                item.getSourceId(),
                pkg.getPatientId(),
                encounter.getId(),
                encounter.getFacilityId(),
                vm.toFacilityId() != null ? vm.toFacilityId() : encounter.getFacilityId(),
                encounter.getDepartmentId(),
                vm.toDepartmentId() != null ? vm.toDepartmentId() : encounter.getDepartmentId(),
                vm.indicationId(),
                vm.procedureLevel(),
                vm.priority(),
                vm.bodyPart().trim(),
                vm.side(),
                vm.scheduledDateTime(),
                vm.notes(),
                null,
                null,
                vm.acceptUncoveredAsCash()
        ));
        return procedure.getId();
    }

    /** Service line with source PACKAGE pointing to the usage row, priced from the service setup. */
    private Long orderService(
            PatientPackage pkg, PatientPackageItem item, PatientEncounter encounter,
            PatientPackageUsage usage, int quantity, PatientPackageOrderVM vm
    ) {
        List<?> rows = entityManager
                .createNativeQuery("select s.price, s.currency from service s where s.id = :id")
                .setParameter("id", item.getSourceId())
                .getResultList();
        if (rows.isEmpty()) {
            throw new NotFoundAlertException("Service not found with id " + item.getSourceId(), ENTITY, "service.notfound");
        }
        Object[] priceRow = (Object[]) rows.get(0);
        BigDecimal price = priceRow[0] != null ? new BigDecimal(priceRow[0].toString()) : BigDecimal.ZERO;
        Currency currency = priceRow[1] != null ? Currency.valueOf(priceRow[1].toString()) : null;
        if (currency == null) {
            throw new BadRequestAlertException("The service has no currency in its setup", ENTITY, "service.currency");
        }

        PatientServiceAndProduct line = patientServiceAndProductService.create(new PatientServiceProductCreateDTO(
                pkg.getPatientId(),
                encounter.getId(),
                BillingItemTypes.SERVICE,
                null,
                null,
                item.getSourceId(),
                null,
                (long) quantity,
                price,
                currency,
                ServiceSource.PACKAGE,
                usage.getId(),
                vm.notes() != null && !vm.notes().isBlank() ? vm.notes() : "Package: " + pkg.getPackageName(),
                vm.acceptUncoveredAsCash(),
                null,
                null
        ));
        return line.getId();
    }
}
