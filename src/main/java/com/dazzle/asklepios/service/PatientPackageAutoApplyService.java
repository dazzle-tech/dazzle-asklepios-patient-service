package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Takes an order from the visit's package when it matches a package item that still has room,
 * wherever the order was made (package panel or the usual order screens).
 * <p>
 * Matching: an item line for the same item first, then a category line of the same type whose
 * repeat limit for that item is not reached. One unit per order; orders beyond the room left are
 * simply not counted (charged as usual). Never throws, so it cannot stop an order.
 * Depends on repositories only, so the order services can call it without a dependency cycle.
 */
@Service
@Transactional
public class PatientPackageAutoApplyService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPackageAutoApplyService.class);

    /** order_source of a service line made from the usual services screen. */
    public static final String SOURCE_SERVICE_LINE = "SERVICE";

    private final PatientPackageRepository packageRepository;
    private final PatientPackageItemRepository itemRepository;
    private final PatientPackageUsageRepository usageRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    public PatientPackageAutoApplyService(
            PatientPackageRepository packageRepository,
            PatientPackageItemRepository itemRepository,
            PatientPackageUsageRepository usageRepository,
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository
    ) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.usageRepository = usageRepository;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }

    /** A diagnostic order was submitted: its tests are counted in the order they were added. */
    public void onDiagnosticOrderSubmitted(Long diagnosticOrderId) {
        DiagnosticOrder order = diagnosticOrderRepository.findById(diagnosticOrderId).orElse(null);
        if (order == null || order.getEncounterId() == null || !hasActivePackage(order.getEncounterId())) {
            return;
        }
        List<DiagnosticOrderTest> tests = new ArrayList<>(diagnosticOrderTestRepository.findByOrderIdInAndStatusNot(
                List.of(diagnosticOrderId), DiagnosticOrderTestStatus.CANCELLED));
        tests.sort(Comparator.comparing(DiagnosticOrderTest::getId));
        for (DiagnosticOrderTest test : tests) {
            BillingItemTypes type = diagnosticType(test.getOrderType());
            if (type == null || test.getTestId() == null) {
                continue;
            }
            if (!usageRepository.findByOrderSourceInAndOrderIdAndStatus(
                    PatientPackageUsageReversalService.DIAGNOSTIC_SOURCES, test.getId(), PatientPackageUsageStatus.USED).isEmpty()) {
                continue; // already counted
            }
            consume(order.getEncounterId(), type, test.getTestId(), type.name(), test.getId());
        }
    }

    /** A procedure was ordered. */
    public void onProcedureCreated(PatientProcedure procedure) {
        if (procedure == null || procedure.getEncounter() == null || procedure.getProcedureId() == null) {
            return;
        }
        consume(procedure.getEncounter().getId(), BillingItemTypes.PROCEDURE, procedure.getProcedureId(),
                PatientPackageUsageReversalService.SOURCE_PROCEDURE, procedure.getId());
    }

    /** A service line was added from the services screen (package lines are counted by the package panel itself). */
    public void onServiceLineCreated(PatientServiceAndProduct line) {
        if (line == null || line.getServiceSource() != ServiceSource.SERVICE_AND_PRODUCT
                || line.getBillingItemType() != BillingItemTypes.SERVICE || line.getServiceId() == null) {
            return;
        }
        consume(line.getEncounterId(), BillingItemTypes.SERVICE, line.getServiceId(), SOURCE_SERVICE_LINE, line.getId());
    }

    /**
     * Counts one unit of the item against the visit's packages, if one matches and has room.
     * Returns the usage written, or empty when nothing matched.
     */
    public Optional<PatientPackageUsage> consume(
            Long encounterId, BillingItemTypes type, Long sourceId, String orderSource, Long orderId
    ) {
        if (encounterId == null || type == null || sourceId == null) {
            return Optional.empty();
        }
        List<PatientPackage> packages = packageRepository.findByEncounterIdOrderByIdAsc(encounterId).stream()
                .filter(p -> p.getStatus() == PatientPackageStatus.ACTIVE)
                .toList();
        if (packages.isEmpty()) {
            return Optional.empty();
        }

        List<PatientPackageItem> lines = new ArrayList<>();
        for (PatientPackage pkg : packages) {
            lines.addAll(itemRepository.findByPatientPackageIdOrderByIdAsc(pkg.getId()));
        }
        // the item's own line first, then category lines of its type
        List<PatientPackageItem> candidates = new ArrayList<>();
        lines.stream()
                .filter(i -> i.getItemType() == type && !Boolean.TRUE.equals(i.getIsCategory()) && sourceId.equals(i.getSourceId()))
                .forEach(candidates::add);
        lines.stream()
                .filter(i -> i.getItemType() == type && Boolean.TRUE.equals(i.getIsCategory()))
                .forEach(candidates::add);

        for (PatientPackageItem candidate : candidates) {
            PatientPackageItem item = itemRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (item == null || item.getQuantityRemaining() < 1 || repeatLimitReached(item, sourceId)) {
                continue;
            }
            PatientPackageUsage usage = usageRepository.save(PatientPackageUsage.builder()
                    .patientPackageId(item.getPatientPackageId())
                    .patientPackageItemId(item.getId())
                    .encounterId(encounterId)
                    .sourceId(sourceId)
                    .quantity(1)
                    .orderSource(orderSource)
                    .orderId(orderId)
                    .status(PatientPackageUsageStatus.USED)
                    .build());
            item.setQuantityUsed((item.getQuantityUsed() == null ? 0 : item.getQuantityUsed()) + 1);
            itemRepository.saveAndFlush(item);
            completeIfUsedUp(item.getPatientPackageId());
            LOG.info("Package item {} took {} {} (order {} id={}) in encounter {}",
                    item.getId(), type, sourceId, orderSource, orderId, encounterId);
            return Optional.of(usage);
        }
        return Optional.empty();
    }

    /** Category lines can count the same item only maxRepeat times (no limit when empty). */
    private boolean repeatLimitReached(PatientPackageItem item, Long sourceId) {
        if (!Boolean.TRUE.equals(item.getIsCategory()) || item.getMaxRepeat() == null) {
            return false;
        }
        long used = usageRepository.countByPatientPackageItemIdAndSourceIdAndStatus(
                item.getId(), sourceId, PatientPackageUsageStatus.USED);
        return used >= item.getMaxRepeat();
    }

    private void completeIfUsedUp(Long patientPackageId) {
        PatientPackage pkg = packageRepository.findById(patientPackageId).orElse(null);
        if (pkg == null || pkg.getStatus() != PatientPackageStatus.ACTIVE) {
            return;
        }
        if (itemRepository.findByPatientPackageIdOrderByIdAsc(pkg.getId()).stream().allMatch(i -> i.getQuantityRemaining() == 0)) {
            pkg.setStatus(PatientPackageStatus.COMPLETED);
            packageRepository.save(pkg);
        }
    }

    private boolean hasActivePackage(Long encounterId) {
        return packageRepository.findByEncounterIdOrderByIdAsc(encounterId).stream()
                .anyMatch(p -> p.getStatus() == PatientPackageStatus.ACTIVE);
    }

    /** Microbiology tests count as laboratory. */
    static BillingItemTypes diagnosticType(TestType testType) {
        if (testType == null) {
            return null;
        }
        return switch (testType) {
            case LABORATORY, MICROBIOLOGY -> BillingItemTypes.LABORATORY;
            case RADIOLOGY -> BillingItemTypes.RADIOLOGY;
            case PATHOLOGY -> BillingItemTypes.PATHOLOGY;
        };
    }

    static final Set<BillingItemTypes> DIAGNOSTIC_TYPES =
            Set.of(BillingItemTypes.LABORATORY, BillingItemTypes.RADIOLOGY, BillingItemTypes.PATHOLOGY);
}
