package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WaseelClaimUploadResponse(
        Long transcationLogId,
        String message,
        Long uploadId,
        Long providerId,
        String uploadName,
        OffsetDateTime uploadDate,
        Integer noOfNotUploadedClaims,
        Integer noOfUploadedClaims,
        BigDecimal totalAmtOfUploadedClaims,
        Integer noOfAcceptedClaims,
        BigDecimal totalAmtOfAcceptedClaims,
        Integer noOfNotAcceptedClaims,
        BigDecimal totalAmtOfNotAcceptedClaims,
        OffsetDateTime lastModifiedDate,
        BigDecimal ratioOfAccepted,
        BigDecimal ratioOfNotAccepted
) {}
