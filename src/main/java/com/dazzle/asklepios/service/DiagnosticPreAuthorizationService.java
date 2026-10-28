package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pre-authorization of diagnostic tests, read from each test's billing line
 * (service source LABORATORY / RADIOLOGY, source id = order test id).
 *
 * - Lists show the status per test.
 * - On NPHIES coverage contracts a lab sample cannot be collected (and so no barcode
 *   label can be printed) while the test waits on pre-authorization, and a radiology test
 *   is not sent to its department on submit until the decision. Waseel is not affected.
 */
@Service
@Transactional(readOnly = true)
public class DiagnosticPreAuthorizationService {

    public static final String ERROR_KEY = "diagnostic.preAuthorizationPending";

    static final String MESSAGE =
            "This test requires pre-authorization approval. Sample collection and barcode printing "
                    + "are not allowed until it is approved. Please contact the Finance Department.";

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticPreAuthorizationService.class);

    private static final Set<ServiceSource> DIAGNOSTIC_SOURCES =
            EnumSet.of(ServiceSource.LABORATORY, ServiceSource.RADIOLOGY);

    private static final EnumSet<BillingChargeLineStatus> INACTIVE_LINE_STATUSES =
            EnumSet.of(BillingChargeLineStatus.CANCELLED, BillingChargeLineStatus.REVERSED);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final CoverageApprovalRoutingService coverageApprovalRoutingService;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    public DiagnosticPreAuthorizationService(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            BillingChargeLineRepository billingChargeLineRepository,
            CoverageApprovalRoutingService coverageApprovalRoutingService,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.billingChargeLineRepository = billingChargeLineRepository;
        this.coverageApprovalRoutingService = coverageApprovalRoutingService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    /**
     * Radiology tests that stay NEW on order submit: their billing line waits on pre-authorization.
     */
    public Set<Long> heldRadiologyTestIds(Collection<DiagnosticOrderTest> tests) {
        List<Long> radiologyTestIds = tests.stream()
                .filter(test -> test.getOrderType() == TestType.RADIOLOGY)
                .map(DiagnosticOrderTest::getId)
                .filter(Objects::nonNull)
                .toList();
        if (radiologyTestIds.isEmpty()) {
            return Set.of();
        }

        Map<Long, PatientServiceAndProduct> billingLines = billingLinesByTestId(radiologyTestIds);
        Set<Long> blockedLineIds = blockedLineIds(billingLines.values());
        return billingLines.entrySet()
                .stream()
                .filter(entry -> blockedLineIds.contains(entry.getValue().getId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Sends a radiology test held on submit to its department once its billing line may be
     * billed (approved, partially approved, or paid as cash after a rejection).
     */
    @Transactional
    public void releaseHeldTest(PatientServiceAndProduct billingLine) {
        if (billingLine == null
                || billingLine.getServiceSource() != ServiceSource.RADIOLOGY
                || billingLine.getSourceId() == null) {
            return;
        }

        diagnosticOrderTestRepository.findById(billingLine.getSourceId())
                .filter(test -> test.getStatus() == DiagnosticOrderTestStatus.NEW)
                .filter(test -> diagnosticOrderRepository.findById(test.getOrderId())
                        .map(order -> Boolean.FALSE.equals(order.getSaveDraft()))
                        .orElse(false))
                .ifPresent(test -> {
                    test.setStatus(DiagnosticOrderTestStatus.SUBMITTED);
                    diagnosticOrderTestRepository.save(test);
                    diagnosticOrderStatusService.recomputeLabRadStatuses(test.getOrderId());
                    LOG.info(
                            "[DIAGNOSTIC_PREAUTH] Radiology test released to its department. testId={} pspId={}",
                            test.getId(),
                            billingLine.getId()
                    );
                });
    }

    /**
     * Sets the transient pre-authorization fields of each test: the status of its billing line
     * ({@code null} when no pre-authorization applies) and whether sample collection is blocked.
     */
    public void attachStatuses(Collection<DiagnosticOrderTest> tests) {
        if (tests == null || tests.isEmpty()) {
            return;
        }

        Map<Long, PatientServiceAndProduct> billingLines = billingLinesByTestId(
                tests.stream().map(DiagnosticOrderTest::getId).filter(Objects::nonNull).toList()
        );
        Set<Long> blockedLineIds = blockedLineIds(billingLines.values());

        for (DiagnosticOrderTest test : tests) {
            PatientServiceAndProduct line = billingLines.get(test.getId());
            PreAuthorizationStatus status = line == null ? null : line.getPreAuthorizationStatus();
            test.setPreAuthorizationStatus(status == PreAuthorizationStatus.NOT_REQUIRED ? null : status);
            test.setPreAuthorizationBlocked(line != null && blockedLineIds.contains(line.getId()));
        }
    }

    /**
     * Rejects sample collection (and so barcode printing) for tests that still wait on
     * pre-authorization.
     */
    public void requireCollectable(Collection<Long> orderTestIds) {
        if (orderTestIds == null || orderTestIds.isEmpty()) {
            return;
        }

        if (!blockedLineIds(billingLinesByTestId(orderTestIds).values()).isEmpty()) {
            LOG.info("[DIAGNOSTIC_PREAUTH] Sample collection blocked. orderTestIds={}", orderTestIds);
            throw new BadRequestAlertException(MESSAGE, "diagnostic_order_tests", ERROR_KEY);
        }
    }

    public void requireCollectable(Long orderTestId) {
        requireCollectable(List.of(orderTestId));
    }

    /**
     * Billing lines of NPHIES-contract visits that wait on the payer decision, or on the
     * patient's choice after a rejection / partial approval (a declined line that has a
     * charge line was accepted by the patient as cash).
     */
    private Set<Long> blockedLineIds(Collection<PatientServiceAndProduct> lines) {
        Map<Long, List<PatientServiceAndProduct>> waitingByEncounter = lines.stream()
                .filter(line -> line.getEncounterId() != null)
                .filter(line -> line.getPaymentStatus() != PaymentStatus.CANCELLED)
                .filter(line -> line.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL
                        || PreAuthorizationResolutionService.isDeclinedByPayer(line))
                .collect(Collectors.groupingBy(PatientServiceAndProduct::getEncounterId));

        Set<Long> blocked = new HashSet<>();
        waitingByEncounter.forEach((encounterId, waiting) -> {
            if (!coverageApprovalRoutingService.encounterUsesLocalApproval(encounterId)) {
                return;
            }

            Set<Long> chargedItemIds = activeChargedItemIds(encounterId);
            waiting.stream()
                    .filter(line -> line.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL
                            || !chargedItemIds.contains(line.getId()))
                    .forEach(line -> blocked.add(line.getId()));
        });
        return blocked;
    }

    private Set<Long> activeChargedItemIds(Long encounterId) {
        return billingChargeLineRepository
                .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(encounterId, INACTIVE_LINE_STATUSES)
                .stream()
                .filter(line -> line.getPatientServiceProduct() != null)
                .map(line -> line.getPatientServiceProduct().getId())
                .collect(Collectors.toSet());
    }

    private Map<Long, PatientServiceAndProduct> billingLinesByTestId(Collection<Long> orderTestIds) {
        if (orderTestIds.isEmpty()) {
            return Map.of();
        }

        return patientServiceAndProductRepository
                .findByServiceSourceInAndSourceIdIn(DIAGNOSTIC_SOURCES, orderTestIds)
                .stream()
                .collect(Collectors.toMap(
                        PatientServiceAndProduct::getSourceId,
                        Function.identity(),
                        (first, second) -> first.getId() > second.getId() ? first : second
                ));
    }
}
