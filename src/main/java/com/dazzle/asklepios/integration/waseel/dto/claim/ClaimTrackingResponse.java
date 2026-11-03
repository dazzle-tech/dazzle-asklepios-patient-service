package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClaimTrackingResponse(
        Long id,
        Long patientId,
        Long encounterId,
        Long financialDocumentId,
        Long preAuthorizationId,
        Long patientInsuranceId,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType,
        String uploadName,
        Long uploadId,
        String provClaimNo,
        String claimReference,
        String settlementNo,
        String preAuthRefNo,
        Long approvalResponseId,
        BigDecimal totalNet,
        BigDecimal approvedAmount,
        WaseelClaimStatus waseelStatus,
        ClaimStatus status,
        String outcome,
        String message,
        Instant submittedAt,
        Instant createdDate,
        String createdBy,
        Instant lastModifiedDate,
        String lastModifiedBy,
        boolean canResubmit,
        boolean canRefreshUpload,
        List<ClaimTrackingItemResponse> items,
        List<ClaimValidationError> validationErrors,
        String statusDescription
) {}
