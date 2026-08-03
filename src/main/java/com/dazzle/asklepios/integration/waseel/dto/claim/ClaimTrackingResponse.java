package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;

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
        String uploadName,
        Long uploadId,
        String provClaimNo,
        String claimReference,
        String preAuthRefNo,
        Long approvalResponseId,
        BigDecimal totalNet,
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
        List<ClaimTrackingItemResponse> items
) {}
