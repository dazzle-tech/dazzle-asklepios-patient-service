package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimSubmissionResponse(
        Long id,
        Long encounterId,
        Long financialDocumentId,
        Long preAuthorizationId,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType,
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
