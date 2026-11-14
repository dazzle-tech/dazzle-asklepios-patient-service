package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.EncounterPreAuthorizationRefreshResponse;
import com.dazzle.asklepios.integration.waseel.dto.EncounterPreAuthorizationRefreshResponse.RefreshedPreAuthorizationItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import com.dazzle.asklepios.integration.waseel.event.PreAuthorizationApprovedEvent;
import com.dazzle.asklepios.integration.waseel.event.PreAuthorizationDecisionCommittedEvent;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.CoverageApprovalRoutingService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PreAuthorizationStatusRefreshService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationStatusRefreshService.class);

    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final WaseelPreAuthorizationService waseelPreAuthorizationService;
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CoverageApprovalRoutingService coverageApprovalRoutingService;
    private final PreAuthorizationLineReleaseService preAuthorizationLineReleaseService;

    @Transactional
    public EncounterPreAuthorizationRefreshResponse refreshEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "encounterId is required",
                    "preAuthorization",
                    "encounter.required"
            );
        }

        if (!encounterInsuranceEligibilityService.isInsuranceEncounter(encounterId)) {
            throw new BadRequestAlertException(
                    "Pre-authorization refresh is only available for insurance encounters.",
                    "preAuthorization",
                    "encounter.notInsurance"
            );
        }

        /*
         * NPHIES contracts keep their requests locally (no Waseel request id).
         * Their decisions are read from the database instead of the Waseel search API.
         */
        boolean localApproval = coverageApprovalRoutingService.encounterUsesLocalApproval(encounterId);

        List<PreAuthorizationRequest> requests =
                preAuthorizationRequestRepository.findByEncounterIdOrderByIdDesc(encounterId)
                        .stream()
                        .filter(request -> request.getApprovalRequestId() != null
                                || (localApproval && isActiveLocalRequest(request)))
                        .toList();

        if (requests.isEmpty()) {
            return buildEmptyResponse(
                    encounterId,
                    "No submitted pre-authorization requests found for this encounter."
            );
        }

        int refreshedRequestCount = 0;
        int approvedItemCount = 0;
        int rejectedItemCount = 0;
        int pendingItemCount = 0;
        List<RefreshedPreAuthorizationItem> refreshedItems = new ArrayList<>();
        List<Long> approvedPatientItemIds = new ArrayList<>();

        for (PreAuthorizationRequest request : requests) {
            // A cancelled request is not read back from Waseel: its lines wait for a new pre-authorization.
            if (Boolean.TRUE.equals(request.getIsCancelled())) {
                pendingItemCount += preAuthorizationLineReleaseService.release(request);
                continue;
            }

            refreshedRequestCount++;

            PropagationResult propagationResult =
                    request.getApprovalRequestId() == null
                            ? propagateLocalRequest(request)
                            : propagateSearchResponse(
                                    request,
                                    waseelPreAuthorizationService.searchAndUpdate(
                                            request.getId(),
                                            request.getApprovalRequestId()
                                    )
                            );

            approvedItemCount += propagationResult.approvedCount();
            rejectedItemCount += propagationResult.rejectedCount();
            pendingItemCount += propagationResult.pendingCount();
            refreshedItems.addAll(propagationResult.items());
            approvedPatientItemIds.addAll(propagationResult.approvedPatientItemIds());
        }

        publishDecisions(encounterId, approvedPatientItemIds, approvedItemCount, rejectedItemCount);

        boolean canCloseCalculation = pendingItemCount == 0;

        String message = canCloseCalculation
                ? "Pre-authorization statuses refreshed. No pending payer decisions remain."
                : "Pre-authorization statuses refreshed. "
                        + pendingItemCount
                        + " item(s) are still pending payer approval.";

        LOG.info(
                "[PREAUTH_REFRESH] encounterId={} requests={} approved={} rejected={} pending={} canClose={}",
                encounterId,
                refreshedRequestCount,
                approvedItemCount,
                rejectedItemCount,
                pendingItemCount,
                canCloseCalculation
        );

        return new EncounterPreAuthorizationRefreshResponse(
                encounterId,
                refreshedRequestCount,
                approvedItemCount,
                rejectedItemCount,
                pendingItemCount,
                canCloseCalculation,
                message,
                refreshedItems
        );
    }

    /**
     * Refresh of one request from the pre-authorization screen. The decision also reaches its
     * billing lines, so a rejected line waits on the patient's choice as it does after the
     * refresh in the patient account. A cancelled or replaced request no longer owns any line.
     */
    @Transactional
    public PreAuthorizationSearchResponse refreshRequest(Long preAuthorizationId, Long requestId) {
        PreAuthorizationSearchResponse searchResponse =
                waseelPreAuthorizationService.searchAndUpdate(preAuthorizationId, requestId);

        PreAuthorizationRequest request = (preAuthorizationId != null
                ? preAuthorizationRequestRepository.findById(preAuthorizationId)
                : preAuthorizationRequestRepository.findFirstByApprovalRequestIdOrderByIdDesc(requestId))
                .orElse(null);

        if (request == null) {
            return searchResponse;
        }

        // A cancelled request no longer owns its lines, whatever status Waseel still returns for it.
        if (Boolean.TRUE.equals(request.getIsCancelled())) {
            preAuthorizationLineReleaseService.release(request);
            return searchResponse;
        }

        if (patientServiceAndProductRepository.findByPreAuthorizationRequestId(request.getId()).isEmpty()) {
            return searchResponse;
        }

        PropagationResult propagationResult = propagateSearchResponse(request, searchResponse);

        publishDecisions(
                request.getEncounterId(),
                propagationResult.approvedPatientItemIds(),
                propagationResult.approvedCount(),
                propagationResult.rejectedCount()
        );

        return searchResponse;
    }

    /** Approved lines are billed, and any decision refreshes the visit's responsibility split. */
    private void publishDecisions(Long encounterId, List<Long> approvedPatientItemIds, int approvedCount, int rejectedCount) {
        if (!approvedPatientItemIds.isEmpty()) {
            applicationEventPublisher.publishEvent(new PreAuthorizationApprovedEvent(encounterId, approvedPatientItemIds));
        }
        if (approvedCount > 0 || rejectedCount > 0) {
            applicationEventPublisher.publishEvent(new PreAuthorizationDecisionCommittedEvent(encounterId));
        }
    }

    /**
     * Temporary local-only refresh for test environments where Waseel search API is unavailable.
     * Propagates statuses already stored on pre_authorization_request / pre_authorization_item
     * into patient_services_and_products without calling Waseel.
     */
    @Transactional
    public EncounterPreAuthorizationRefreshResponse refreshEncounterLocalOnly(Long encounterId) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "encounterId is required",
                    "preAuthorization",
                    "encounter.required"
            );
        }

        if (!encounterInsuranceEligibilityService.isInsuranceEncounter(encounterId)) {
            throw new BadRequestAlertException(
                    "Pre-authorization refresh is only available for insurance encounters.",
                    "preAuthorization",
                    "encounter.notInsurance"
            );
        }

        List<PreAuthorizationRequest> requests =
                preAuthorizationRequestRepository.findByEncounterIdOrderByIdDesc(encounterId)
                        .stream()
                        .filter(this::isLocallyRefreshableRequest)
                        .toList();

        if (requests.isEmpty()) {
            return buildEmptyResponse(
                    encounterId,
                    "No local pre-authorization requests found for this encounter."
            );
        }

        int refreshedRequestCount = 0;
        int approvedItemCount = 0;
        int rejectedItemCount = 0;
        int pendingItemCount = 0;
        List<RefreshedPreAuthorizationItem> refreshedItems = new ArrayList<>();
        List<Long> approvedPatientItemIds = new ArrayList<>();

        for (PreAuthorizationRequest request : requests) {
            PropagationResult propagationResult = propagateLocalRequest(request);

            refreshedRequestCount++;
            approvedItemCount += propagationResult.approvedCount();
            rejectedItemCount += propagationResult.rejectedCount();
            pendingItemCount += propagationResult.pendingCount();
            refreshedItems.addAll(propagationResult.items());
            approvedPatientItemIds.addAll(propagationResult.approvedPatientItemIds());
        }

        // Local test sync stays lightweight — use refreshEncounter() for Waseel + billing trigger.
        boolean canCloseCalculation = pendingItemCount == 0;

        String message = canCloseCalculation
                ? "Pre-authorization statuses synced from local database. No pending payer decisions remain."
                : "Pre-authorization statuses synced from local database. "
                        + pendingItemCount
                        + " item(s) are still pending payer approval.";

        LOG.info(
                "[PREAUTH_REFRESH_LOCAL] encounterId={} requests={} approved={} rejected={} pending={} canClose={}",
                encounterId,
                refreshedRequestCount,
                approvedItemCount,
                rejectedItemCount,
                pendingItemCount,
                canCloseCalculation
        );

        return new EncounterPreAuthorizationRefreshResponse(
                encounterId,
                refreshedRequestCount,
                approvedItemCount,
                rejectedItemCount,
                pendingItemCount,
                canCloseCalculation,
                message,
                refreshedItems
        );
    }

    /**
     * A local request still linked to its billing line. A request replaced by a clone
     * is skipped, so its old decision cannot overwrite the new request.
     */
    private boolean isActiveLocalRequest(PreAuthorizationRequest request) {
        return isLocallyRefreshableRequest(request)
                && !patientServiceAndProductRepository.findByPreAuthorizationRequestId(request.getId()).isEmpty();
    }

    private boolean isLocallyRefreshableRequest(PreAuthorizationRequest request) {
        if (request == null) {
            return false;
        }

        if (request.getApprovalRequestId() != null) {
            return true;
        }

        String status = request.getStatus();
        return status != null
                && !status.isBlank()
                && !"DRAFT".equalsIgnoreCase(status.trim());
    }

    private PropagationResult propagateLocalRequest(PreAuthorizationRequest request) {
        List<PatientServiceAndProduct> linkedItems = resolveLinkedPatientItems(request);

        List<PreAuthorizationItem> localItems =
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(
                        request.getId()
                );

        PreAuthorizationStatus headerStatus = PreAuthorizationWaseelStatusMapper.mapStatusAndOutcome(
                request.getStatus(),
                request.getOutcome()
        );

        String headerWaseelStatus = firstNonBlank(request.getStatus(), request.getOutcome());

        boolean useHeaderForAllItems =
                localItems.isEmpty()
                        && headerStatus != PreAuthorizationStatus.PENDING_APPROVAL;

        int approvedCount = 0;
        int rejectedCount = 0;
        int pendingCount = 0;
        List<RefreshedPreAuthorizationItem> refreshedItems = new ArrayList<>();
        List<Long> approvedPatientItemIds = new ArrayList<>();

        if (useHeaderForAllItems) {
            for (PatientServiceAndProduct item : linkedItems) {
                ItemRefreshOutcome outcome =
                        applyResolvedStatus(
                                item,
                                request,
                                headerStatus,
                                headerWaseelStatus
                        );

                approvedCount += outcome.approved() ? 1 : 0;
                rejectedCount += outcome.rejected() ? 1 : 0;
                pendingCount += outcome.pending() ? 1 : 0;
                refreshedItems.add(outcome.item());
                if (outcome.approved()) {
                    approvedPatientItemIds.add(item.getId());
                }
            }
        } else {
            Map<Long, PatientServiceAndProduct> assignedItems = new HashMap<>();

            for (PreAuthorizationItem localItem : localItems) {
                PreAuthorizationStatus itemStatus =
                        PreAuthorizationWaseelStatusMapper.mapStatusText(
                                firstNonBlank(localItem.getItemDecision(), request.getStatus(), request.getOutcome())
                        );

                String waseelStatus = firstNonBlank(
                        localItem.getItemDecision(),
                        request.getStatus(),
                        request.getOutcome()
                );

                PatientServiceAndProduct patientItem =
                        findPatientItem(localItem, linkedItems, assignedItems);

                if (patientItem == null) {
                    LOG.warn(
                            "[PREAUTH_REFRESH_LOCAL] Unable to map pre-auth item to billing row. preAuthId={} sequence={} itemCode={}",
                            request.getId(),
                            localItem.getSequence(),
                            localItem.getItemCode()
                    );
                    continue;
                }

                assignedItems.put(patientItem.getId(), patientItem);

                ItemRefreshOutcome outcome =
                        applyResolvedStatus(
                                patientItem,
                                request,
                                itemStatus,
                                waseelStatus
                        );

                approvedCount += outcome.approved() ? 1 : 0;
                rejectedCount += outcome.rejected() ? 1 : 0;
                pendingCount += outcome.pending() ? 1 : 0;
                refreshedItems.add(outcome.item());
                if (outcome.approved()) {
                    approvedPatientItemIds.add(patientItem.getId());
                }
            }

            for (PatientServiceAndProduct item : linkedItems) {
                if (assignedItems.containsKey(item.getId())) {
                    continue;
                }

                ItemRefreshOutcome outcome =
                        applyResolvedStatus(
                                item,
                                request,
                                headerStatus,
                                headerWaseelStatus
                        );

                approvedCount += outcome.approved() ? 1 : 0;
                rejectedCount += outcome.rejected() ? 1 : 0;
                pendingCount += outcome.pending() ? 1 : 0;
                refreshedItems.add(outcome.item());
                if (outcome.approved()) {
                    approvedPatientItemIds.add(item.getId());
                }
            }
        }

        patientServiceAndProductRepository.saveAll(linkedItems);

        return new PropagationResult(
                approvedCount,
                rejectedCount,
                pendingCount,
                refreshedItems,
                approvedPatientItemIds
        );
    }

    private List<PatientServiceAndProduct> resolveLinkedPatientItems(
            PreAuthorizationRequest request
    ) {
        List<PatientServiceAndProduct> linkedItems =
                patientServiceAndProductRepository.findByPreAuthorizationRequestId(request.getId());

        if (!linkedItems.isEmpty()) {
            return linkedItems;
        }

        List<PreAuthorizationItem> localItems =
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(
                        request.getId()
                );

        if (localItems.isEmpty()) {
            return List.of();
        }

        List<PatientServiceAndProduct> encounterItems =
                patientServiceAndProductRepository.findByEncounterId(request.getEncounterId());

        List<PatientServiceAndProduct> matched = new ArrayList<>();

        for (PreAuthorizationItem localItem : localItems) {
            encounterItems.stream()
                    .filter(item -> matchesCatalogIds(localItem, item))
                    .findFirst()
                    .ifPresent(matched::add);
        }

        return matched;
    }

    private PropagationResult propagateSearchResponse(
            PreAuthorizationRequest request,
            PreAuthorizationSearchResponse searchResponse
    ) {
        List<PatientServiceAndProduct> linkedItems = resolveLinkedPatientItems(request);

        List<PreAuthorizationItem> localItems =
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(request.getId());

        Map<Integer, PreAuthorizationSearchItem> searchItemsBySequence =
                indexSearchItems(searchResponse);

        PreAuthorizationStatus headerStatus =
                PreAuthorizationWaseelStatusMapper.mapHeaderStatus(searchResponse);

        boolean useHeaderForAllItems =
                searchItemsBySequence.isEmpty()
                        && headerStatus != PreAuthorizationStatus.PENDING_APPROVAL;

        int approvedCount = 0;
        int rejectedCount = 0;
        int pendingCount = 0;
        List<RefreshedPreAuthorizationItem> refreshedItems = new ArrayList<>();
        List<Long> approvedPatientItemIds = new ArrayList<>();

        if (useHeaderForAllItems) {
            for (PatientServiceAndProduct item : linkedItems) {
                ItemRefreshOutcome outcome =
                        applyResolvedStatus(
                                item,
                                request,
                                headerStatus,
                                firstNonBlank(
                                        searchResponse == null ? null : searchResponse.status(),
                                        searchResponse == null ? null : searchResponse.outcome()
                                )
                        );

                approvedCount += outcome.approved() ? 1 : 0;
                rejectedCount += outcome.rejected() ? 1 : 0;
                pendingCount += outcome.pending() ? 1 : 0;
                refreshedItems.add(outcome.item());
                if (outcome.approved()) {
                    approvedPatientItemIds.add(item.getId());
                }
            }
        } else {
            Map<Long, PatientServiceAndProduct> assignedItems = new HashMap<>();

            for (PreAuthorizationItem localItem : localItems) {
                PreAuthorizationSearchItem searchItem =
                        findSearchItem(localItem, searchItemsBySequence);

                WaseelItemDecision decision = searchItem == null ? null : WaseelItemDecision.of(searchItem);
                if (decision != null) {
                    recordDecision(localItem, decision);
                }

                PreAuthorizationStatus itemStatus = decision != null ? decision.status() : headerStatus;

                String waseelStatus = decision != null
                        ? decision.decisionText()
                        : firstNonBlank(
                                searchResponse == null ? null : searchResponse.status(),
                                searchResponse == null ? null : searchResponse.outcome()
                        );

                PatientServiceAndProduct patientItem =
                        findPatientItem(localItem, linkedItems, assignedItems);

                if (patientItem == null) {
                    LOG.warn(
                            "[PREAUTH_REFRESH] Unable to map pre-auth item to billing row. preAuthId={} sequence={} itemCode={}",
                            request.getId(),
                            localItem.getSequence(),
                            localItem.getItemCode()
                    );
                    continue;
                }

                assignedItems.put(patientItem.getId(), patientItem);

                ItemRefreshOutcome outcome =
                        applyResolvedStatus(
                                patientItem,
                                request,
                                itemStatus,
                                waseelStatus
                        );

                approvedCount += outcome.approved() ? 1 : 0;
                rejectedCount += outcome.rejected() ? 1 : 0;
                pendingCount += outcome.pending() ? 1 : 0;
                refreshedItems.add(outcome.item());
                if (outcome.approved()) {
                    approvedPatientItemIds.add(patientItem.getId());
                }
            }

            for (PatientServiceAndProduct item : linkedItems) {
                if (assignedItems.containsKey(item.getId())) {
                    continue;
                }

                ItemRefreshOutcome outcome =
                        applyResolvedStatus(
                                item,
                                request,
                                headerStatus,
                                firstNonBlank(
                                        searchResponse == null ? null : searchResponse.status(),
                                        searchResponse == null ? null : searchResponse.outcome()
                                )
                        );

                approvedCount += outcome.approved() ? 1 : 0;
                rejectedCount += outcome.rejected() ? 1 : 0;
                pendingCount += outcome.pending() ? 1 : 0;
                refreshedItems.add(outcome.item());
                if (outcome.approved()) {
                    approvedPatientItemIds.add(item.getId());
                }
            }
        }

        patientServiceAndProductRepository.saveAll(linkedItems);
        preAuthorizationItemRepository.saveAll(localItems);

        return new PropagationResult(
                approvedCount,
                rejectedCount,
                pendingCount,
                refreshedItems,
                approvedPatientItemIds
        );
    }

    /**
     * Keeps the payer amounts on the request line: billing reads the payer share as the insurance cap
     * of a partial approval, and the reason is shown on the patient account.
     */
    private static void recordDecision(PreAuthorizationItem localItem, WaseelItemDecision decision) {
        localItem.setItemDecision(decision.status() == PreAuthorizationStatus.PARTIAL ? "partial" : decision.decisionText());
        if (decision.reason() != null) {
            localItem.setReasonCodes(decision.reason());
        }
        if (decision.approvedInsuranceAmount() != null) {
            localItem.setPayerShare(decision.approvedInsuranceAmount());
            if (decision.submitted() != null) {
                localItem.setPatientShare(decision.submitted().subtract(decision.approvedInsuranceAmount()).max(BigDecimal.ZERO));
            }
        }
    }

    private ItemRefreshOutcome applyResolvedStatus(
            PatientServiceAndProduct item,
            PreAuthorizationRequest request,
            PreAuthorizationStatus status,
            String waseelStatus
    ) {
        item.setPreAuthorizationRequestId(request.getId());
        item.setPreAuthorizationReferenceNo(request.getPreAuthRefNo());

        PreAuthorizationStatus previousStatus = item.getPreAuthorizationStatus();
        item.setPreAuthorizationStatus(status);

        switch (status) {
            case APPROVED -> {
                item.setPreAuthorizationRequired(false);
                updatePaymentStatus(item, PaymentStatus.PENDING);
            }
            case REJECTED -> {
                item.setPreAuthorizationRequired(false);
                updatePaymentStatus(item, PaymentStatus.PENDING);
            }
            case PARTIAL -> {
                // A local (NPHIES contract) partial decision is final: the line is billed with
                // the approved insurance amount. A Waseel partial waits for the patient's choice:
                // pay the difference (accept) or cancel the service.
                boolean localDecision = request.getApprovalRequestId() == null;
                boolean acceptedByPatient = previousStatus == PreAuthorizationStatus.PARTIAL
                        && !Boolean.TRUE.equals(item.getPreAuthorizationRequired());
                boolean billable = localDecision || acceptedByPatient;
                item.setPreAuthorizationRequired(!billable);
                updatePaymentStatus(
                        item,
                        billable
                                ? PaymentStatus.PENDING
                                : PaymentStatus.SKIPPED_PENDING_PRE_AUTH
                );
            }
            default -> {
                item.setPreAuthorizationRequired(true);
                updatePaymentStatus(item, PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
            }
        }

        RefreshedPreAuthorizationItem refreshedItem =
                new RefreshedPreAuthorizationItem(
                        item.getId(),
                        request.getId(),
                        request.getApprovalRequestId(),
                        status,
                        waseelStatus,
                        status == PreAuthorizationStatus.REJECTED,
                        status == PreAuthorizationStatus.REJECTED
                );

        boolean billable =
                status == PreAuthorizationStatus.APPROVED
                        || PreAuthorizationResolutionService.isPartiallyApprovedLocally(item);
        boolean newlyApproved = billable && previousStatus != status;

        return new ItemRefreshOutcome(
                newlyApproved,
                status == PreAuthorizationStatus.REJECTED,
                PreAuthorizationWaseelStatusMapper.isPendingStatus(status)
                        && Boolean.TRUE.equals(item.getPreAuthorizationRequired()),
                refreshedItem
        );
    }

    private Map<Integer, PreAuthorizationSearchItem> indexSearchItems(
            PreAuthorizationSearchResponse searchResponse
    ) {
        Map<Integer, PreAuthorizationSearchItem> indexed = new LinkedHashMap<>();

        if (searchResponse == null || searchResponse.item() == null) {
            return indexed;
        }

        for (PreAuthorizationSearchItem searchItem : searchResponse.item()) {
            if (searchItem == null) {
                continue;
            }

            if (searchItem.sequence() != null) {
                indexed.put(searchItem.sequence(), searchItem);
            }

            if (searchItem.itemDecision() != null
                    && searchItem.itemDecision().itemSequence() != null) {
                indexed.putIfAbsent(
                        searchItem.itemDecision().itemSequence(),
                        searchItem
                );
            }
        }

        return indexed;
    }

    /**
     * A line already posted to the patient account (for example one sent for approval after the
     * visit crossed its coverage limit) keeps its ledger payment status; the decision only updates
     * its approval status and the responsibility refresh.
     */
    private static void updatePaymentStatus(PatientServiceAndProduct item, PaymentStatus paymentStatus) {
        if (!Boolean.TRUE.equals(item.getIsBilled())) {
            item.setPaymentStatus(paymentStatus);
        }
    }

    private PreAuthorizationSearchItem findSearchItem(
            PreAuthorizationItem localItem,
            Map<Integer, PreAuthorizationSearchItem> searchItemsBySequence
    ) {
        if (localItem == null || searchItemsBySequence.isEmpty()) {
            return null;
        }

        if (localItem.getSequence() != null) {
            PreAuthorizationSearchItem bySequence =
                    searchItemsBySequence.get(localItem.getSequence());
            if (bySequence != null) {
                return bySequence;
            }
        }

        if (localItem.getItemCode() != null) {
            for (PreAuthorizationSearchItem searchItem : searchItemsBySequence.values()) {
                if (searchItem.itemCode() != null
                        && searchItem.itemCode().equalsIgnoreCase(localItem.getItemCode())) {
                    return searchItem;
                }
            }
        }

        return null;
    }

    private PatientServiceAndProduct findPatientItem(
            PreAuthorizationItem localItem,
            List<PatientServiceAndProduct> linkedItems,
            Map<Long, PatientServiceAndProduct> assignedItems
    ) {
        for (PatientServiceAndProduct item : linkedItems) {
            if (assignedItems.containsKey(item.getId())) {
                continue;
            }

            if (matchesCatalogIds(localItem, item)) {
                return item;
            }
        }

        if (localItem.getSequence() != null) {
            int index = localItem.getSequence() - 1;
            if (index >= 0 && index < linkedItems.size()) {
                PatientServiceAndProduct candidate = linkedItems.get(index);
                if (!assignedItems.containsKey(candidate.getId())) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private boolean matchesCatalogIds(
            PreAuthorizationItem localItem,
            PatientServiceAndProduct item
    ) {
        return PreAuthorizationItemMatcher.matches(localItem, item);
    }

    private EncounterPreAuthorizationRefreshResponse buildEmptyResponse(
            Long encounterId,
            String message
    ) {
        return new EncounterPreAuthorizationRefreshResponse(
                encounterId,
                0,
                0,
                0,
                0,
                true,
                message,
                List.of()
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private record PropagationResult(
            int approvedCount,
            int rejectedCount,
            int pendingCount,
            List<RefreshedPreAuthorizationItem> items,
            List<Long> approvedPatientItemIds
    ) {}

    private record ItemRefreshOutcome(
            boolean approved,
            boolean rejected,
            boolean pending,
            RefreshedPreAuthorizationItem item
    ) {}
}
