package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementLogAction;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;

import java.time.Instant;

public record ClaimSettlementLogResponse(
        Long id,
        Long settlementId,
        String settlementNo,
        Long financialDocumentId,
        Long claimRequestId,
        ClaimSettlementLogAction action,
        ClaimStatus claimStatus,
        String note,
        Instant createdDate,
        String createdBy
) {
}
