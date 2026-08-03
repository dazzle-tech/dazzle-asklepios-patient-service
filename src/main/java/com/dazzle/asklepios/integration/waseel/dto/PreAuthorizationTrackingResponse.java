package com.dazzle.asklepios.integration.waseel.dto;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CancelReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PreAuthorizationTrackingResponse(
        Long id,

        Long patientId,
        Long encounterId,
        Long patientInsuranceId,
        Long payorId,
        Long payorPlanId,

        String providerId,
        String providerNphiesId,

        Long transactionId,
        String outgoingTransactionId,
        Long approvalRequestId,
        Long approvalResponseId,
        String preAuthRefNo,

        String eligibilityResponseId,
        String eligibilityResponseUrl,
        String eligibilityOfflineId,
        LocalDate eligibilityOfflineDate,

        LocalDate dateOrdered,

        Long payeeId,
        String payeeType,

        String preauthType,
        String preauthSubType,

        String episodeId,
        String prescription,

        Boolean transfer,
        Boolean isNewBorn,
        String destinationId,

        String encounterStatus,
        String encounterClass,
        String serviceType,
        String serviceEventType,
        Long serviceProvider,
        LocalDate encounterStartDate,
        LocalDate encounterEndDate,

        BigDecimal totalNet,

        String status,
        String outcome,
        String message,
        String disposition,
        String statusReason,

        Boolean isCancelled,
        CancelReason cancelReason,
        String cancelStatus,
        String cancelOutcome,
        String cancelMessage,

        Boolean searchCompleted,
        Boolean canCommunicate,
        Boolean canCancel,
        List<Long> waseelClaimItemIds,
        List<PreAuthorizationTrackingItemResponse> items,
        Long communicationCount,

        Instant createdDate,
        String createdBy,
        Instant lastModifiedDate,
        String lastModifiedBy
) {}
