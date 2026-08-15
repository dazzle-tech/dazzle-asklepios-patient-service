package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingItemResponse;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingResponse;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.repository.PreAuthorizationTrackRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PreAuthorizationTrackingService {

    private final PreAuthorizationRequestRepository repository;
    private final PreAuthorizationItemRepository itemRepository;
    private final PreAuthorizationTrackRepository trackRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    public Page<PreAuthorizationTrackingResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public PreAuthorizationTrackingResponse findById(Long id) {
        PreAuthorizationRequest entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization not found with id " + id,
                        "preAuthorization",
                        "notfound"
                ));

        return toResponse(entity);
    }

    private PreAuthorizationTrackingResponse toResponse(PreAuthorizationRequest e) {
        List<PreAuthorizationItem> items =
                itemRepository.findByPreAuthorizationIdOrderBySequenceAsc(e.getId());

        List<Long> waseelClaimItemIds = items.stream()
                .map(PreAuthorizationItem::getWaseelItemId)
                .filter(id -> id != null)
                .toList();

        List<PreAuthorizationTrackingItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        boolean cancelled = Boolean.TRUE.equals(e.getIsCancelled());
        boolean searchCompleted = e.getSearchResponseJson() != null
                && !e.getSearchResponseJson().isBlank();
        boolean canCommunicate = !cancelled
                && (e.getApprovalResponseId() != null || searchCompleted);
        boolean canCancel = !cancelled && e.getApprovalRequestId() != null;
        boolean canResubmit = PreAuthorizationResubmissionService.canResubmit(e)
                && hasLinkedUnbilledItems(e.getId());
        long communicationCount = trackRepository.countByPreAuthorization_IdAndTrackType(
                e.getId(),
                PreAuthorizationCommunicationHistoryService.TRACK_TYPE_COMMUNICATION
        );

        return new PreAuthorizationTrackingResponse(
                e.getId(),

                e.getPatientId(),
                e.getEncounterId(),
                e.getPatientInsuranceId(),
                e.getPayorId(),
                e.getPayorPlanId(),

                e.getProviderId(),
                e.getProviderNphiesId(),

                e.getTransactionId(),
                e.getOutgoingTransactionId(),
                e.getApprovalRequestId(),
                e.getApprovalResponseId(),
                e.getPreAuthRefNo(),

                e.getEligibilityResponseId(),
                e.getEligibilityResponseUrl(),
                e.getEligibilityOfflineId(),
                e.getEligibilityOfflineDate(),

                e.getDateOrdered(),

                e.getPayeeId(),
                e.getPayeeType(),

                e.getPreauthType(),
                e.getPreauthSubType(),

                e.getEpisodeId(),
                e.getPrescription(),

                e.getTransfer(),
                e.getIsNewBorn(),
                e.getDestinationId(),

                e.getEncounterStatus(),
                e.getEncounterClass(),
                e.getServiceType(),
                e.getServiceEventType(),
                e.getServiceProvider(),
                e.getEncounterStartDate(),
                e.getEncounterEndDate(),

                e.getTotalNet(),

                resolveDisplayStatus(e),
                e.getOutcome(),
                e.getMessage(),
                e.getDisposition(),
                e.getStatusReason(),

                e.getIsCancelled(),
                e.getCancelReason(),
                e.getCancelStatus(),
                e.getCancelOutcome(),
                e.getCancelMessage(),

                searchCompleted,
                canCommunicate,
                canCancel,
                canResubmit,
                waseelClaimItemIds,
                itemResponses,
                communicationCount,

                e.getCreatedDate(),
                e.getCreatedBy(),
                e.getLastModifiedDate(),
                e.getLastModifiedBy()
        );
    }

    private boolean hasLinkedUnbilledItems(Long preAuthorizationId) {
        return patientServiceAndProductRepository
                .existsByPreAuthorizationRequestIdAndIsBilledFalse(preAuthorizationId);
    }

    private PreAuthorizationTrackingItemResponse toItemResponse(PreAuthorizationItem item) {
        return new PreAuthorizationTrackingItemResponse(
                item.getId(),
                item.getSequence(),
                item.getItemType(),
                item.getItemCode(),
                item.getItemDescription(),
                item.getNonStandardCode(),
                item.getNonStandardDesc(),
                item.getIsPackage(),
                item.getIsMaternity(),
                item.getQuantity(),
                item.getQuantityCode(),
                item.getUnitPrice(),
                item.getDiscount(),
                item.getFactor(),
                item.getTaxPercent(),
                item.getTax(),
                item.getPatientSharePercent(),
                item.getPatientShare(),
                item.getPayerShare(),
                item.getNet(),
                item.getStartDate(),
                item.getEndDate(),
                item.getWaseelItemId(),
                item.getItemDecision(),
                item.getReasonCodes()
        );
    }

    /**
     * Waseel may keep the original approval status (e.g. "pended") after cancel while outcome becomes "Cancelled".
     * When isCancelled is true, prefer cancel-specific fields for the Status column shown in the UI.
     */
    private String resolveDisplayStatus(PreAuthorizationRequest request) {
        if (Boolean.TRUE.equals(request.getIsCancelled())) {
            return firstNonBlank(
                    request.getCancelStatus(),
                    request.getCancelOutcome(),
                    request.getOutcome(),
                    "Cancelled"
            );
        }

        return request.getStatus();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }
}
