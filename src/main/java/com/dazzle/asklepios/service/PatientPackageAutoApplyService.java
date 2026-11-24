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
 * <p>
 * Orders from the package panel are counted on the line the user chose instead
 * ({@link #withoutAutoApply} + {@link #consumeLine}), so the matching above is skipped for them.
 * Depends on repositories only, so the order services can call it without a dependency cycle.
 */
@Service
@Transactional
public class PatientPackageAutoApplyService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPackageAutoApplyService.class);

    /** order_source of a patient_services_and_products line counted automatically (service, UCC dose, vaccination). */
    public static final String SOURCE_SERVICE_LINE = "SERVICE";

    /** Set while the package panel makes its own orders: the hooks below then do nothing. */
    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Runs the work with the automatic matching turned off (the caller counts on the chosen line). */
    public static <T> T withoutAutoApply(java.util.function.Supplier<T> work) {
        boolean before = SUPPRESSED.get();
        SUPPRESSED.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            SUPPRESSED.set(before);
        }
    }

    private static boolean suppressed() {
        return Boolean.TRUE.equals(SUPPRESSED.get());
    }

    private final PatientPackageRepository packageRepository;
    private final PatientPackageItemRepository itemRepository;
    private final PatientPackageUsageRepository usageRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private PatientPackageUsageReversalService reversalService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setReversalService(PatientPackageUsageReversalService reversalService) {
        this.reversalService = reversalService;
    }

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
        if (suppressed()) {
            return;
        }
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
        if (suppressed() || procedure == null || procedure.getEncounter() == null || procedure.getProcedureId() == null) {
            return;
        }
        consume(procedure.getEncounter().getId(), BillingItemTypes.PROCEDURE, procedure.getProcedureId(),
                PatientPackageUsageReversalService.SOURCE_PROCEDURE, procedure.getId());
    }

    /**
     * A charge line was added: a service from the services screen, a UCC medication dose when it is given,
     * or a vaccination when it is recorded. Package service lines are counted by the package panel itself.
     */
    public void onServiceLineCreated(PatientServiceAndProduct line) {
        if (suppressed() || line == null || line.getServiceSource() == null || line.getBillingItemType() == null) {
            return;
        }
        // counted once: a line seen again (e.g. the visit's default service when the package is given) is skipped
        if (line.getId() != null && !usageRepository.findByOrderSourceInAndOrderIdAndStatus(
                List.of(SOURCE_SERVICE_LINE), line.getId(), PatientPackageUsageStatus.USED).isEmpty()) {
            return;
        }
        Long sourceId = switch (line.getServiceSource()) {
            case SERVICE_AND_PRODUCT, ENCOUNTER_DEFAULT_SERVICE ->
                    line.getBillingItemType() == BillingItemTypes.SERVICE ? line.getServiceId() : null;
            case URGENT_CARE_MEDICATION_ORDER ->
                    line.getBillingItemType() == BillingItemTypes.MEDICATION ? line.getBrandMedicationId() : null;
            case VACCINATION -> line.getBillingItemType() == BillingItemTypes.VACCINE ? line.getVaccineBrandId() : null;
            default -> null; // prescriptions and the rest are not taken from packages
        };
        if (sourceId == null) {
            return;
        }
        // a service line (and the visit's default service) counts its quantity; a UCC dose or a vaccination counts once
        int quantity = line.getBillingItemType() == BillingItemTypes.SERVICE && line.getQuantity() != null
                ? (int) Math.max(1L, line.getQuantity())
                : 1;
        consume(line.getEncounterId(), line.getBillingItemType(), sourceId, SOURCE_SERVICE_LINE, line.getId(), quantity);
    }

    /**
     * The package was just given to the visit: the visit's default service (made when the visit opened)
     * is taken from it if the package covers it.
     */
    public void onPackageGiven(Long encounterId, List<PatientServiceAndProduct> encounterLines) {
        if (suppressed() || encounterId == null || encounterLines == null) {
            return;
        }
        encounterLines.stream()
                .filter(l -> l.getServiceSource() == ServiceSource.ENCOUNTER_DEFAULT_SERVICE)
                .filter(l -> l.getPaymentStatus() != com.dazzle.asklepios.domain.enumeration.PaymentStatus.CANCELLED)
                .sorted(Comparator.comparing(PatientServiceAndProduct::getId))
                .forEach(this::onServiceLineCreated);
    }

    /**
     * A counted line's item or quantity was edited: give back what it took and count it again as it is now.
     * Lines ordered from the package panel (source PACKAGE) are left as they are.
     */
    public void onServiceLineUpdated(PatientServiceAndProduct line) {
        if (suppressed() || line == null || line.getId() == null || line.getServiceSource() == ServiceSource.PACKAGE) {
            return;
        }
        if (reversalService != null) {
            reversalService.onServiceCancelled(line.getId());
        }
        onServiceLineCreated(line);
    }

    /**
     * Counts one unit of the item against the visit's packages, if one matches and has room.
     * Returns the usage written, or empty when nothing matched.
     */
    public Optional<PatientPackageUsage> consume(
            Long encounterId, BillingItemTypes type, Long sourceId, String orderSource, Long orderId
    ) {
        return consume(encounterId, type, sourceId, orderSource, orderId, 1).stream().findFirst();
    }

    /**
     * Counts up to {@code quantity} units, spread over the matching lines in order (own item line, then category).
     * Units beyond the room left are not counted. Returns the usages written (empty when nothing matched).
     */
    public List<PatientPackageUsage> consume(
            Long encounterId, BillingItemTypes type, Long sourceId, String orderSource, Long orderId, int quantity
    ) {
        if (encounterId == null || type == null || sourceId == null || quantity < 1) {
            return List.of();
        }
        List<PatientPackage> packages = packageRepository.findByEncounterIdOrderByIdAsc(encounterId).stream()
                .filter(p -> p.getStatus() == PatientPackageStatus.ACTIVE)
                .toList();
        if (packages.isEmpty()) {
            return List.of();
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

        List<PatientPackageUsage> written = new ArrayList<>();
        int left = quantity;
        for (PatientPackageItem candidate : candidates) {
            if (left < 1) {
                break;
            }
            Optional<PatientPackageUsage> usage = consumeLine(candidate.getId(), encounterId, sourceId, orderSource, orderId, left);
            if (usage.isPresent()) {
                written.add(usage.get());
                left -= usage.get().getQuantity();
            }
        }
        return written;
    }

    /**
     * Counts one unit on this exact package line (the one chosen in the panel), if it still has room and
     * the repeat limit allows it. Empty when there is no room: the order is then charged as usual.
     */
    public Optional<PatientPackageUsage> consumeLine(
            Long patientPackageItemId, Long encounterId, Long sourceId, String orderSource, Long orderId
    ) {
        return consumeLine(patientPackageItemId, encounterId, sourceId, orderSource, orderId, 1);
    }

    /** As above for up to {@code quantity} units: takes what the line's room and repeat limit allow. */
    public Optional<PatientPackageUsage> consumeLine(
            Long patientPackageItemId, Long encounterId, Long sourceId, String orderSource, Long orderId, int quantity
    ) {
        PatientPackageItem item = itemRepository.findByIdForUpdate(patientPackageItemId).orElse(null);
        if (item == null || quantity < 1) {
            return Optional.empty();
        }
        int take = Math.min(quantity, Math.min(item.getQuantityRemaining(), repeatRoom(item, sourceId)));
        if (take < 1) {
            return Optional.empty();
        }
        PatientPackage pkg = packageRepository.findById(item.getPatientPackageId()).orElse(null);
        if (pkg == null || pkg.getStatus() != PatientPackageStatus.ACTIVE) {
            return Optional.empty();
        }
        PatientPackageUsage usage = usageRepository.save(PatientPackageUsage.builder()
                .patientPackageId(item.getPatientPackageId())
                .patientPackageItemId(item.getId())
                .encounterId(encounterId)
                .sourceId(sourceId)
                .quantity(take)
                .orderSource(orderSource)
                .orderId(orderId)
                .status(PatientPackageUsageStatus.USED)
                .build());
        item.setQuantityUsed((item.getQuantityUsed() == null ? 0 : item.getQuantityUsed()) + take);
        itemRepository.saveAndFlush(item);
        completeIfUsedUp(item.getPatientPackageId());
        LOG.info("Package item {} took {} {} (order {} id={}) in encounter {}",
                item.getId(), item.getItemType(), sourceId, orderSource, orderId, encounterId);
        return Optional.of(usage);
    }

    /** Category lines can count the same item only maxRepeat times (no limit when empty). */
    private int repeatRoom(PatientPackageItem item, Long sourceId) {
        if (!Boolean.TRUE.equals(item.getIsCategory()) || item.getMaxRepeat() == null) {
            return Integer.MAX_VALUE;
        }
        long used = usageRepository.sumQuantity(item.getId(), sourceId, PatientPackageUsageStatus.USED);
        return (int) Math.max(0, item.getMaxRepeat() - used);
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
