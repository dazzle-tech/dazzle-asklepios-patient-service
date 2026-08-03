package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimSubmissionResponse(
        Long id,
        Long encounterId,
        Long financialDocumentId,
        Long preAuthorizationId,
        String uploadName,
        Long uploadId,
        String provClaimNo,
        String claimReference,
        String preAuthRefNo,
        BigDecimal totalNet,
        ClaimStatus status,
        String outcome,
        String message,
        Instant submittedAt
) {}
