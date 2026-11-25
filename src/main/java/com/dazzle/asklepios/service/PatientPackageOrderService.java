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
    private final PatientPackageAutoApplyService autoApplyService;

    public PatientPackageOrderService(
            PatientPackageRepository packageRepository,
            PatientPackageItemRepository itemRepository,
            PatientPackageUsageRepository usageRepository,
            PatientEncounterRepository encounterRepository,
            DiagnosticOrderService diagnosticOrderService,
            DiagnosticOrderTestService diagnosticOrderTestService,
            PatientProcedureService patientProcedureService,
            PatientServiceAndProductService patientServiceAndProductService,
            EntityManager entityManager,
            PatientPackageAutoApplyService autoApplyService
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
        this.autoApplyService = autoApplyService;
    }

    public record OrderResultVM(
            Long usageId,
            String orderSource,
            Long orderId,
            ResponseVM patientPackage
    ) implements Serializable {}

    /**
     * Orders one package item from the panel and counts it on that line (not on another package that has
     * the same item). Tests and procedures are made with the usual services with the automatic matching off;
     * services are written here with source PACKAGE. Category lines are ordered through {@link #orderDiagnostics}.
     */
    public OrderResultVM order(Long itemId, PatientPackageOrderVM vm) {
        PatientPackageItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new NotFoundAlertException("Package item not found with id " + itemId, ENTITY, "item.notfound"));
        PatientPackage pkg = packageRepository.findById(item.getPatientPackageId())
                .orElseThrow(() -> new NotFoundAlertException("Patient package not found", ENTITY, "notfound"));
        PatientEncounter encounter = encounterRepository.findById(vm.encounterId())
                .orElseThrow(() -> new NotFoundAlertException("Encounter not found with id " + vm.encounterId(), ENTITY, "encounter.notfound"));
        checkPackageForVisit(pkg, encounter);
        if (Boolean.TRUE.equals(item.getIsCategory())) {
            throw new BadRequestAlertException(
                    "Choose the item to order for " + item.getItemType().name().toLowerCase() + " in the package", ENTITY, "item.category");
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

        String orderSource;
        Long orderId;
        switch (type) {
            case LABORATORY, RADIOLOGY, PATHOLOGY -> {
                orderSource = type.name();
                orderId = PatientPackageAutoApplyService.withoutAutoApply(() -> orderDiagnosticTest(pkg, item, encounter, vm));
                autoApplyService.consumeLine(item.getId(), encounter.getId(), item.getSourceId(), orderSource, orderId)
                        .ifPresent(this::markFromPanel);
            }
            case PROCEDURE -> {
                orderSource = PatientPackageUsageReversalService.SOURCE_PROCEDURE;
                orderId = PatientPackageAutoApplyService.withoutAutoApply(() -> orderProcedure(pkg, item, encounter, vm));
                autoApplyService.consumeLine(item.getId(), encounter.getId(), item.getSourceId(), orderSource, orderId)
                        .ifPresent(this::markFromPanel);
            }
            case SERVICE -> {
                // The usage row is written first: the service line points back to it through source_id.
                PatientPackageUsage usage = usageRepository.saveAndFlush(PatientPackageUsage.builder()
                        .patientPackageId(pkg.getId())
                        .patientPackageItemId(item.getId())
                        .encounterId(encounter.getId())
                        .sourceId(item.getSourceId())
                        .quantity(quantity)
                        .status(PatientPackageUsageStatus.USED)
                        .fromPanel(true)
                        .build());
                orderSource = PatientPackageUsageReversalService.SOURCE_SERVICE;
                orderId = orderService(pkg, item, encounter, usage, quantity, vm);
                usage.setOrderSource(orderSource);
                usage.setOrderId(orderId);
                usageRepository.save(usage);
                PatientPackageItem locked = itemRepository.findByIdForUpdate(item.getId()).orElse(item);
                locked.setQuantityUsed((locked.getQuantityUsed() == null ? 0 : locked.getQuantityUsed()) + quantity);
                itemRepository.saveAndFlush(locked);
            }
            default -> throw new BadRequestAlertException(
                    "Ordering " + type.name().toLowerCase() + " items from a package is not available yet", ENTITY, "item.typeNotSupported");
        }

        completeIfUsedUp(pkg);
        Long usageId = usageRepository.findByOrderSourceInAndOrderIdAndStatus(
                        List.of(orderSource), orderId, PatientPackageUsageStatus.USED).stream()
                .map(PatientPackageUsage::getId).findFirst().orElse(null);
        List<PatientPackageItem> items = itemRepository.findByPatientPackageIdOrderByIdAsc(pkg.getId());
        LOG.info("Package {} item {} ({}) ordered in encounter {}: {} id={} qty={} usage={}",
                pkg.getId(), item.getId(), item.getItemName(), encounter.getId(), orderSource, orderId, quantity, usageId);
        return new OrderResultVM(usageId, orderSource, orderId, ResponseVM.ofEntity(pkg, items));
    }

    public record DiagnosticsOrderResultVM(
            Long diagnosticOrderId,
            List<Long> diagnosticOrderTestIds,
            /** Tests taken from the package; the others were over the room left and are charged as usual. */
            List<Long> countedTestIds,
            List<ResponseVM> patientPackages
    ) implements Serializable {}

    /**
     * Several tests in ONE diagnostic order, submitted once. A line is either an item line (its own test)
     * or a category line with the test picked. Each test is counted on the line it was picked under, in the
     * order sent: once that line has no room left (or the test reached its repeat limit) the rest are still
     * ordered but charged as usual.
     */
    public DiagnosticsOrderResultVM orderDiagnostics(PatientPackageDiagnosticsOrderVM vm) {
        PatientEncounter encounter = encounterRepository.findById(vm.encounterId())
                .orElseThrow(() -> new NotFoundAlertException("Encounter not found with id " + vm.encounterId(), ENTITY, "encounter.notfound"));
        Long patientId = encounter.getPatient() != null ? encounter.getPatient().getId() : null;

        Map<Long, PatientPackageItem> itemsById = new HashMap<>();
        Map<Long, PatientPackage> packagesById = new LinkedHashMap<>();
        Set<Long> testIds = new HashSet<>();
        List<Long> lineTestIds = new ArrayList<>();
        for (PatientPackageDiagnosticsOrderVM.Line line : vm.items()) {
            PatientPackageItem item = itemsById.computeIfAbsent(line.itemId(), id -> itemRepository.findById(id)
                    .orElseThrow(() -> new NotFoundAlertException("Package item not found with id " + id, ENTITY, "item.notfound")));
            if (!PatientPackageUsageReversalService.DIAGNOSTIC_SOURCES.contains(item.getItemType().name())) {
                throw new BadRequestAlertException(
                        (item.getItemName() != null ? item.getItemName() : item.getItemType().name()) + " is not a diagnostic test",
                        ENTITY, "item.notDiagnostic");
            }
            PatientPackage pkg = packagesById.computeIfAbsent(item.getPatientPackageId(), pid -> packageRepository.findById(pid)
                    .orElseThrow(() -> new NotFoundAlertException("Patient package not found", ENTITY, "notfound")));
            checkPackageForVisit(pkg, encounter);

            Long testId = Boolean.TRUE.equals(item.getIsCategory()) ? line.testId() : item.getSourceId();
            if (testId == null) {
                throw new BadRequestAlertException("Choose the test for each " + item.getItemType().name().toLowerCase() + " line",
                        ENTITY, "item.testRequired");
            }
            if (!testIds.add(testId)) {
                throw new BadRequestAlertException("The same test is selected twice", ENTITY, "item.duplicateTest");
            }
            lineTestIds.add(testId);
        }

        // made with the automatic matching off: each test is counted below on the line it was picked under
        List<Long> createdTestIds = new ArrayList<>();
        DiagnosticOrder order = PatientPackageAutoApplyService.withoutAutoApply(
                () -> createAndSubmitTests(vm, encounter, patientId, itemsById, lineTestIds, createdTestIds));

        List<Long> countedTestIds = new ArrayList<>();
        for (int i = 0; i < vm.items().size(); i++) {
            PatientPackageItem item = itemsById.get(vm.items().get(i).itemId());
            Long testOrderId = createdTestIds.get(i);
            autoApplyService.consumeLine(item.getId(), encounter.getId(), lineTestIds.get(i), item.getItemType().name(), testOrderId)
                    .ifPresent(u -> {
                        markFromPanel(u);
                        countedTestIds.add(testOrderId);
                    });
        }

        List<ResponseVM> packages = new ArrayList<>();
        for (PatientPackage pkg : packagesById.values()) {
            PatientPackage fresh = packageRepository.findById(pkg.getId()).orElse(pkg);
            packages.add(ResponseVM.ofEntity(fresh, itemRepository.findByPatientPackageIdOrderByIdAsc(pkg.getId())));
        }

        LOG.info("Package tests ordered in encounter {}: diagnostic order {} with {} tests, {} taken from the package",
                encounter.getId(), order.getId(), createdTestIds.size(), countedTestIds.size());
        return new DiagnosticsOrderResultVM(order.getId(), createdTestIds, countedTestIds, packages);
    }

    private DiagnosticOrder createAndSubmitTests(
            PatientPackageDiagnosticsOrderVM vm, PatientEncounter encounter, Long patientId,
            Map<Long, PatientPackageItem> itemsById, List<Long> lineTestIds, List<Long> createdTestIds
    ) {
        DiagnosticOrder order = diagnosticOrderService.create(new DiagnosticOrderCreateDTO(
                patientId,
                encounter.getId(),
                Boolean.TRUE.equals(vm.isUrgent()),
                null,
                null,
                encounter.getDepartmentId(),
                encounter.getFacilityId()
        ));
        for (int i = 0; i < vm.items().size(); i++) {
            PatientPackageDiagnosticsOrderVM.Line line = vm.items().get(i);
            PatientPackageItem item = itemsById.get(line.itemId());
            DiagnosticOrderTest test = diagnosticOrderTestService.create(new DiagnosticOrderTestCreateDTO(
                    order.getId(),
                    lineTestIds.get(i),
                    line.receivedDepartmentId(),
                    line.reason(),
                    vm.notes(),
                    TestType.valueOf(item.getItemType().name()),
                    vm.icdDiagnosisId(),
                    vm.acceptUncoveredAsCash()
            ));
            requireReceivingDepartment(test);
            createdTestIds.add(test.getId());
        }

        diagnosticOrderService.submit(order.getId(), SecurityUtils.getCurrentUserLogin().orElse("system"));
        return order;
    }

    private void checkPackageForVisit(PatientPackage pkg, PatientEncounter encounter) {
        if (pkg.getStatus() != PatientPackageStatus.ACTIVE) {
            throw new BadRequestAlertException(
                    "Package " + pkg.getPackageName() + " is " + pkg.getStatus().name().toLowerCase(), ENTITY, "package.notActive");
        }
        Long encounterPatientId = encounter.getPatient() != null ? encounter.getPatient().getId() : null;
        if (!pkg.getPatientId().equals(encounterPatientId)) {
            throw new BadRequestAlertException("The encounter does not belong to the package's patient", ENTITY, "encounter.patient.mismatch");
        }
        if (!encounter.getId().equals(pkg.getEncounterId())) {
            throw new BadRequestAlertException("Package " + pkg.getPackageName() + " belongs to another visit", ENTITY, "package.otherVisit");
        }
    }

    private void completeIfUsedUp(PatientPackage pkg) {
        PatientPackage fresh = packageRepository.findById(pkg.getId()).orElse(pkg);
        if (fresh.getStatus() == PatientPackageStatus.ACTIVE
                && itemRepository.findByPatientPackageIdOrderByIdAsc(fresh.getId()).stream().allMatch(i -> i.getQuantityRemaining() == 0)) {
            fresh.setStatus(PatientPackageStatus.COMPLETED);
            packageRepository.save(fresh);
        }
    }

    /** The usage was made by an order from the package panel (shown in the history). */
    private void markFromPanel(PatientPackageUsage usage) {
        if (usage != null && usage.getId() != null) {
            usage.setFromPanel(true);
            usageRepository.save(usage);
        }
    }

    /**
     * A test needs the lab / radiology department that receives it, or it reaches no worklist. When none is
     * chosen the facility's default department is used (on creating the test); with no default it is refused.
     */
    private static void requireReceivingDepartment(DiagnosticOrderTest test) {
        if (test.getReceivedDepartmentId() == null) {
            throw new BadRequestAlertException(
                    "Choose the department that receives each test", ENTITY, "test.department.required");
        }
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
                vm.toDepartmentId(),
                vm.reason(),
                vm.notes(),
                TestType.valueOf(item.getItemType().name()),
                vm.icdDiagnosisId(),
                vm.acceptUncoveredAsCash()
        ));
        requireReceivingDepartment(test);
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
