package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.patientEncounter.EncounterPreAuthorizationLockResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A visit on an NPHIES coverage contract cannot start (triage or doctor) while one of its
 * default services still waits on pre-authorization: the payer decision, or the patients
 * choice after a rejection / partial approval (pay as cash or cancel the service).
 * Waseel contracts are not affected.
 */
@Service
@Transactional(readOnly = true)
public class EncounterPreAuthorizationGuardService {

    public static final String ERROR_KEY = "encounter.preAuthorizationPending";

    static final String MESSAGE =
            "This visit requires pre-authorization approval before it can start. "
                    + "Please contact the Finance Department.";

    private static final Logger LOG = LoggerFactory.getLogger(EncounterPreAuthorizationGuardService.class);

    private static final EnumSet<BillingChargeLineStatus> INACTIVE_LINE_STATUSES =
            EnumSet.of(BillingChargeLineStatus.CANCELLED, BillingChargeLineStatus.REVERSED);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final CoverageApprovalRoutingService coverageApprovalRoutingService;

    public EncounterPreAuthorizationGuardService(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            BillingChargeLineRepository billingChargeLineRepository,
            CoverageApprovalRoutingService coverageApprovalRoutingService
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.billingChargeLineRepository = billingChargeLineRepository;
        this.coverageApprovalRoutingService = coverageApprovalRoutingService;
    }

    public void requireNoPendingPreAuthorization(Long encounterId) {
        if (!isLocked(encounterId)) {
            return;
        }

        LOG.info("[VISIT_START] Blocked: default service waits on pre-authorization. encounterId={}", encounterId);
        throw new BadRequestAlertException(MESSAGE, "patientEncounter", ERROR_KEY);
    }

    /**
     * For screens that start work without calling a start endpoint (nurse station).
     */
    public EncounterPreAuthorizationLockResponse lockStatus(Long encounterId) {
        boolean locked = isLocked(encounterId);
        return new EncounterPreAuthorizationLockResponse(locked, locked ? MESSAGE : null);
    }

    private boolean isLocked(Long encounterId) {
        if (encounterId == null) {
            return false;
        }

        List<PatientServiceAndProduct> items = patientServiceAndProductRepository.findByEncounterId(encounterId);
        Set<Long> chargedItemIds = activeChargedItemIds(encounterId);
        boolean waiting = items.stream().anyMatch(item -> blocksVisitStart(item, chargedItemIds));

        // The contract is read only when a line waits, so ordinary visits pay no extra call.
        return waiting && coverageApprovalRoutingService.encounterUsesLocalApproval(encounterId);
    }

    private boolean blocksVisitStart(PatientServiceAndProduct item, Set<Long> chargedItemIds) {
        if (!Boolean.TRUE.equals(item.getIsDefaultService())
                || item.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return false;
        }

        if (item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            return true;
        }

        // A declined line that has a charge line was accepted by the patient as cash.
        return PreAuthorizationResolutionService.isDeclinedByPayer(item)
                && !chargedItemIds.contains(item.getId());
    }

    private Set<Long> activeChargedItemIds(Long encounterId) {
        return billingChargeLineRepository
                .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(encounterId, INACTIVE_LINE_STATUSES)
                .stream()
                .filter(line -> line.getPatientServiceProduct() != null)
                .map(line -> line.getPatientServiceProduct().getId())
                .collect(Collectors.toSet());
    }
}
