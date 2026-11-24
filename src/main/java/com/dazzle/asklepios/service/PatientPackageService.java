package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PackageSetupItemView;
import com.dazzle.asklepios.domain.PackageSetupView;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import com.dazzle.asklepios.repository.PackageSetupItemViewRepository;
import com.dazzle.asklepios.repository.PackageSetupViewRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.AssignVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.ResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Packages bought for patients. Choosing a package copies its items and quantities from the setup;
 * the patient can then order them in that visit only. The full package price is owed
 * whatever is used (billing is not linked yet).
 */
@Service
@Transactional
public class PatientPackageService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPackageService.class);
    private static final String ENTITY = "patientPackage";

    private final PatientPackageRepository patientPackageRepository;
    private final PatientPackageItemRepository patientPackageItemRepository;
    private final PatientPackageUsageRepository patientPackageUsageRepository;
    private final PackageSetupViewRepository packageSetupViewRepository;
    private final PackageSetupItemViewRepository packageSetupItemViewRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private PatientPackageAutoApplyService patientPackageAutoApplyService;
    private com.dazzle.asklepios.repository.PatientServiceAndProductRepository patientServiceAndProductRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public void setPackageDefaultServiceLink(
            PatientPackageAutoApplyService patientPackageAutoApplyService,
            com.dazzle.asklepios.repository.PatientServiceAndProductRepository patientServiceAndProductRepository
    ) {
        this.patientPackageAutoApplyService = patientPackageAutoApplyService;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
    }

    public PatientPackageService(
            PatientPackageRepository patientPackageRepository,
            PatientPackageItemRepository patientPackageItemRepository,
            PatientPackageUsageRepository patientPackageUsageRepository,
            PackageSetupViewRepository packageSetupViewRepository,
            PackageSetupItemViewRepository packageSetupItemViewRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            jakarta.persistence.EntityManager entityManager
    ) {
        this.patientPackageRepository = patientPackageRepository;
        this.patientPackageItemRepository = patientPackageItemRepository;
        this.patientPackageUsageRepository = patientPackageUsageRepository;
        this.packageSetupViewRepository = packageSetupViewRepository;
        this.packageSetupItemViewRepository = packageSetupItemViewRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.entityManager = entityManager;
    }

    /** Gives a package to a patient in a visit: copies the package price and every item with its quantity. */
    public ResponseVM assign(AssignVM vm) {
        LOG.info("[ASSIGN] package={} patient={} encounter={}", vm.packageSetupId(), vm.patientId(), vm.encounterId());

        if (!patientRepository.existsById(vm.patientId())) {
            throw new NotFoundAlertException("Patient not found with id " + vm.patientId(), ENTITY, "patient.notfound");
        }

        PatientEncounter encounter = patientEncounterRepository.findById(vm.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + vm.encounterId(), ENTITY, "encounter.notfound"));
        Long encounterPatientId = encounter.getPatient() != null ? encounter.getPatient().getId() : null;
        if (!vm.patientId().equals(encounterPatientId)) {
            throw new BadRequestAlertException(
                    "The encounter does not belong to this patient", ENTITY, "encounter.patient.mismatch");
        }
        if (patientPackageRepository.existsByEncounterIdAndPackageSetupIdAndStatusNot(
                vm.encounterId(), vm.packageSetupId(), PatientPackageStatus.CANCELLED)) {
            throw new BadRequestAlertException(
                    "This package is already added to this visit", ENTITY, "package.duplicate");
        }

        PackageSetupView setup = packageSetupViewRepository.findById(vm.packageSetupId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Package not found with id " + vm.packageSetupId(), ENTITY, "package.notfound"));
        if (!Boolean.TRUE.equals(setup.getIsActive())) {
            throw new BadRequestAlertException("This package is not active", ENTITY, "package.inactive");
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        if ((setup.getValidFrom() != null && today.isBefore(setup.getValidFrom()))
                || (setup.getValidTo() != null && today.isAfter(setup.getValidTo()))) {
            throw new BadRequestAlertException("This package is not valid today", ENTITY, "package.outOfDate");
        }
        if (Boolean.FALSE.equals(setup.getAllDepartments())) {
            List<?> departments = entityManager
                    .createNativeQuery("select department_id from package_setup_department where package_setup_id = :id")
                    .setParameter("id", setup.getId())
                    .getResultList();
            boolean allowed = encounter.getDepartmentId() != null && departments.stream()
                    .anyMatch(d -> d != null && Long.valueOf(d.toString()).equals(encounter.getDepartmentId()));
            if (!allowed) {
                throw new BadRequestAlertException(
                        "This package is not available in the visit's department", ENTITY, "package.department");
            }
        }

        // inactive items are left out of the patient's package
        List<PackageSetupItemView> setupItems = packageSetupItemViewRepository.findByPackageSetupIdOrderByIdAsc(setup.getId())
                .stream().filter(i -> !Boolean.FALSE.equals(i.getIsActive())).toList();
        if (setupItems.isEmpty()) {
            throw new BadRequestAlertException("This package has no active items", ENTITY, "package.empty");
        }

        PatientPackage saved = patientPackageRepository.saveAndFlush(PatientPackage.builder()
                .patientId(vm.patientId())
                .encounterId(vm.encounterId())
                .packageSetupId(setup.getId())
                .packageCode(setup.getCode())
                .packageName(setup.getName())
                .packagePrice(setup.getPrice())
                .status(PatientPackageStatus.ACTIVE)
                .build());

        List<PatientPackageItem> items = patientPackageItemRepository.saveAllAndFlush(setupItems.stream()
                .map(s -> PatientPackageItem.builder()
                        .patientPackageId(saved.getId())
                        .packageSetupItemId(s.getId())
                        .itemType(s.getItemType())
                        .sourceId(s.getSourceId())
                        .itemCode(s.getItemCode())
                        .itemName(s.getItemName())
                        .quantityTotal(s.getQuantity())
                        .quantityUsed(0)
                        .isCategory(Boolean.TRUE.equals(s.getIsCategory()))
                        .maxRepeat(s.getMaxRepeat())
                        .discountPercent(s.getDiscountPercent())
                        .build())
                .toList());

        if (patientPackageAutoApplyService != null && patientServiceAndProductRepository != null) {
            // the visit's default service, made when the visit opened, is taken from the package if it covers it
            patientPackageAutoApplyService.onPackageGiven(
                    vm.encounterId(),
                    patientServiceAndProductRepository
                            .findAllByEncounterId(vm.encounterId(), org.springframework.data.domain.Pageable.unpaged())
                            .getContent());
            items = patientPackageItemRepository.findByPatientPackageIdOrderByIdAsc(saved.getId());
        }

        LOG.info("Assigned package id={} ({}) to patient={} with {} items",
                saved.getId(), saved.getPackageCode(), saved.getPatientId(), items.size());
        return ResponseVM.ofEntity(saved, items);
    }

    /** The patient's packages, newest first. With activeOnly only those that still have items to order. */
    @Transactional(readOnly = true)
    public List<ResponseVM> findByPatient(Long patientId, boolean activeOnly) {
        List<PatientPackage> packages = activeOnly
                ? patientPackageRepository.findByPatientIdAndStatusInOrderByIdDesc(
                        patientId, EnumSet.of(PatientPackageStatus.ACTIVE))
                : patientPackageRepository.findByPatientIdOrderByIdDesc(patientId);
        return withItems(packages);
    }

    /** Packages chosen in one visit. */
    @Transactional(readOnly = true)
    public List<ResponseVM> findByEncounter(Long encounterId) {
        return withItems(patientPackageRepository.findByEncounterIdOrderByIdAsc(encounterId));
    }

    @Transactional(readOnly = true)
    public ResponseVM findOne(Long id) {
        PatientPackage p = getOrThrow(id);
        return ResponseVM.ofEntity(p, patientPackageItemRepository.findByPatientPackageIdOrderByIdAsc(id));
    }

    /** Cancels a package only while nothing has been ordered from it. */
    public ResponseVM cancel(Long id, String reason) {
        PatientPackage p = getOrThrow(id);
        if (p.getStatus() == PatientPackageStatus.CANCELLED) {
            throw new BadRequestAlertException("The package is already cancelled", ENTITY, "package.cancelled");
        }
        if (patientPackageUsageRepository.existsByPatientPackageIdAndStatus(id, PatientPackageUsageStatus.USED)) {
            throw new BadRequestAlertException(
                    "Items were already ordered from this package, it cannot be cancelled", ENTITY, "package.used");
        }
        p.setStatus(PatientPackageStatus.CANCELLED);
        p.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());
        PatientPackage saved = patientPackageRepository.saveAndFlush(p);
        LOG.info("Cancelled patient package id={}", id);
        return ResponseVM.ofEntity(saved, patientPackageItemRepository.findByPatientPackageIdOrderByIdAsc(id));
    }

    private List<ResponseVM> withItems(List<PatientPackage> packages) {
        if (packages.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PatientPackageItem>> itemsByPackage = patientPackageItemRepository
                .findByPatientPackageIdIn(packages.stream().map(PatientPackage::getId).toList())
                .stream()
                .sorted(Comparator.comparing(PatientPackageItem::getId))
                .collect(Collectors.groupingBy(PatientPackageItem::getPatientPackageId));
        return packages.stream()
                .map(p -> ResponseVM.ofEntity(p, itemsByPackage.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    private PatientPackage getOrThrow(Long id) {
        return patientPackageRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException("Patient package not found with id " + id, ENTITY, "notfound"));
    }
}
