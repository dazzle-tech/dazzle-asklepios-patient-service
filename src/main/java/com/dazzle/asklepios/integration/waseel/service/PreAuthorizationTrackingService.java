package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.PreAuthorizationTrack;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingResponse;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreAuthorizationTrackingService {

    private final PreAuthorizationRequestRepository repository;

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

                e.getStatus(),
                e.getOutcome(),
                e.getMessage(),
                e.getDisposition(),
                e.getStatusReason(),

                e.getIsCancelled(),
                e.getCancelReason(),
                e.getCancelStatus(),
                e.getCancelOutcome(),
                e.getCancelMessage(),

                e.getCreatedDate(),
                e.getCreatedBy(),
                e.getLastModifiedDate(),
                e.getLastModifiedBy()
        );
    }
}