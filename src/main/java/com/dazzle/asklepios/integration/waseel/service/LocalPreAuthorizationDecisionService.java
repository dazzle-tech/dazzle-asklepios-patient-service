package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.EncounterPreAuthorizationRefreshResponse;
import com.dazzle.asklepios.integration.waseel.dto.LocalPreAuthorizationCoverageResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.LocalPreAuthorizationDecisionRequest;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.CoverageApprovalRoutingService;
import com.dazzle.asklepios.service.InsurancePatientShareCalculator;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Records the payer decision on a local pre-approval request (NPHIES contract)
 * and applies it to the visit the same way a Waseel response is applied:
 * approved lines are billed with the contract split, partially approved lines with the
 * approved insurance amount (the patient pays the rest), rejected lines can be paid as cash.
 */
@Service
@RequiredArgsConstructor
public class LocalPreAuthorizationDecisionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(LocalPreAuthorizationDecisionService.class);

    private static final String ENTITY_NAME = "preAuthorization";

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;
    private final CoverageApprovalRoutingService coverageApprovalRoutingService;
    private final PreAuthorizationStatusRefreshService preAuthorizationStatusRefreshService;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;

    /**
     * Contract split of the request line: what insurance would cover on a full approval.
     */
    @Transactional(readOnly = true)
    public LocalPreAuthorizationCoverageResponse coverage(Long preAuthorizationId) {
        PreAuthorizationRequest request = loadRequest(preAuthorizationId);
        PreAuthorizationItem line = requireLine(request);
        BigDecimal net = money(line.getNet());

        PatientServiceAndProduct item = patientServiceAndProductRepository
                .findByPreAuthorizationRequestId(request.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestAlertException(
                        "The billing line of this request was not found.",
                        ENTITY_NAME,
                        "item.notFound"
                ));
        PatientInsurance insurance = patientInsuranceRepository.findById(request.getPatientInsuranceId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "The patient insurance of this request was not found.",
                        ENTITY_NAME,
                        "insurance.notFound"
                ));

        InsuranceSplit split = insurancePatientShareCalculator.calculateSplit(insurance, item, net);
        BigDecimal patientShare = money(split.patientShare());
        return new LocalPreAuthorizationCoverageResponse(net, patientShare, net.subtract(patientShare));
    }

    @Transactional
    public EncounterPreAuthorizationRefreshResponse decide(
            Long preAuthorizationId,
            LocalPreAuthorizationDecisionRequest decisionRequest
    ) {
        if (decisionRequest == null || decisionRequest.decision() == null) {
            throw new BadRequestAlertException("Decision is required.", ENTITY_NAME, "decision.required");
        }

        PreAuthorizationRequest request = loadRequest(preAuthorizationId);

        validateLocalPendingRequest(request);

        boolean partial = decisionRequest.decision() == LocalPreAuthorizationDecisionRequest.Decision.PARTIAL;
        BigDecimal approvedAmount = partial
                ? validatedApprovedAmount(preAuthorizationId, decisionRequest.approvedAmount())
                : null;

        String status = statusText(decisionRequest.decision());
        String itemDecision = status.toLowerCase(Locale.ROOT);

        request.setStatus(status);
        request.setOutcome(status);
        if (decisionRequest.note() != null && !decisionRequest.note().isBlank()) {
            request.setDisposition(decisionRequest.note().trim());
        }
        preAuthorizationRequestRepository.save(request);

        List<PreAuthorizationItem> items =
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(request.getId());
        items.forEach(item -> {
            item.setItemDecision(itemDecision);
            if (approvedAmount != null) {
                // Read by billing as the insurance cap of the partial approval.
                item.setPayerShare(approvedAmount);
                item.setPatientShare(money(item.getNet()).subtract(approvedAmount).max(BigDecimal.ZERO));
            }
        });
        preAuthorizationItemRepository.saveAll(items);

        LOG.info(
                "[PREAUTH_LOCAL_DECISION] preAuthId={} encounterId={} decision={} items={}",
                request.getId(),
                request.getEncounterId(),
                status,
                items.size()
        );

        return preAuthorizationStatusRefreshService.refreshEncounter(request.getEncounterId());
    }

    /**
     * A partial approval needs the approved insurance amount, at most the contract insurance share.
     */
    private BigDecimal validatedApprovedAmount(Long preAuthorizationId, BigDecimal approvedAmount) {
        if (approvedAmount == null || approvedAmount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Enter the insurance amount approved for the partial approval.",
                    ENTITY_NAME,
                    "decision.approvedAmount.required"
            );
        }

        BigDecimal contractInsuranceShare = coverage(preAuthorizationId).insuranceShare();
        BigDecimal amount = money(approvedAmount);
        if (amount.compareTo(contractInsuranceShare) > 0) {
            throw new BadRequestAlertException(
                    "The approved amount cannot be more than the insurance share of the contract ("
                            + contractInsuranceShare.toPlainString() + ").",
                    ENTITY_NAME,
                    "decision.approvedAmount.tooHigh"
            );
        }
        return amount;
    }

    private PreAuthorizationRequest loadRequest(Long preAuthorizationId) {
        return preAuthorizationRequestRepository.findById(preAuthorizationId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization not found with id " + preAuthorizationId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    private PreAuthorizationItem requireLine(PreAuthorizationRequest request) {
        return preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(request.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestAlertException(
                        "This request has no service line.",
                        ENTITY_NAME,
                        "item.notFound"
                ));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Stored as the request status; mapped back by PreAuthorizationWaseelStatusMapper.
     */
    private static String statusText(LocalPreAuthorizationDecisionRequest.Decision decision) {
        return switch (decision) {
            case APPROVED -> "Approved";
            case PARTIAL -> "Partial";
            case REJECTED -> "Rejected";
        };
    }

    private void validateLocalPendingRequest(PreAuthorizationRequest request) {
        if (request.getApprovalRequestId() != null) {
            throw new BadRequestAlertException(
                    "This request was sent to Waseel. Its decision comes from Waseel.",
                    ENTITY_NAME,
                    "decision.waseelRequest"
            );
        }

        if (Boolean.TRUE.equals(request.getIsCancelled())) {
            throw new BadRequestAlertException(
                    "This request is cancelled.",
                    ENTITY_NAME,
                    "decision.cancelled"
            );
        }

        PreAuthorizationStatus current = PreAuthorizationWaseelStatusMapper.mapStatusText(
                request.getStatus()
        );
        if (current != PreAuthorizationStatus.PENDING_APPROVAL) {
            throw new BadRequestAlertException(
                    "A decision was already recorded for this request.",
                    ENTITY_NAME,
                    "decision.alreadyDecided"
            );
        }

        if (!coverageApprovalRoutingService.encounterUsesLocalApproval(request.getEncounterId())) {
            throw new BadRequestAlertException(
                    "Local decisions are only available for NPHIES coverage contracts.",
                    ENTITY_NAME,
                    "decision.notLocalContract"
            );
        }
    }
}
