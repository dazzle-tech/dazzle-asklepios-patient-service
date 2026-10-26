package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimSettlementGroupSummaryResponse(
        Long id,
        String settlementNo,
        ClaimSettlementStatus status,
        Long payorId,
        String payerNphiesId,
        String payerName,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType,
        int invoiceCount,
        int releasedCount,
        int unlinkedCount,
        BigDecimal totalInvoiceAmount,
        BigDecimal totalClaimAmount,
        Instant createdDate,
        String createdBy,
        boolean submittable
) {
}
