package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.EncounterPreAuthorizationRefreshResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.LocalPreAuthorizationDecisionRequest;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.CoverageApprovalRoutingService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Records the payer decision on a local pre-approval request (NPHIES contract)
 * and applies it to the visit the same way a Waseel response is applied:
 * approved lines are billed with the insurance split, rejected lines can be paid as cash.
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

    @Transactional
    public EncounterPreAuthorizationRefreshResponse decide(
            Long preAuthorizationId,
            LocalPreAuthorizationDecisionRequest decisionRequest
    ) {
        if (decisionRequest == null || decisionRequest.decision() == null) {
            throw new BadRequestAlertException("Decision is required.", ENTITY_NAME, "decision.required");
        }

        PreAuthorizationRequest request = preAuthorizationRequestRepository.findById(preAuthorizationId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization not found with id " + preAuthorizationId,
                        ENTITY_NAME,
                        "notfound"
                ));

        validateLocalPendingRequest(request);

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
        items.forEach(item -> item.setItemDecision(itemDecision));
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
