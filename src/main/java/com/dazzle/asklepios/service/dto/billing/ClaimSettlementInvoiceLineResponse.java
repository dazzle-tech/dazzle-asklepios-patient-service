package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementMembershipStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimSettlementInvoiceLineResponse(
        Long financialDocumentId,
        String invoiceNumber,
        Long patientId,
        String patientName,
        String visitNumber,
        String visitType,
        Long claimId,
        ClaimStatus claimStatus,
        String claimReference,
        BigDecimal invoiceAmount,
        BigDecimal claimAmount,
        ClaimSettlementMembershipStatus membershipStatus,
        String releaseReason,
        Instant releasedAt
) {
}
