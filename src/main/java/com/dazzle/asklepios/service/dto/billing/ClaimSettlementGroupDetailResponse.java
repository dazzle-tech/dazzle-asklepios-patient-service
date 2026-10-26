package com.dazzle.asklepios.service.dto.billing;

import java.util.List;

public record ClaimSettlementGroupDetailResponse(
        ClaimSettlementGroupSummaryResponse statement,
        List<ClaimSettlementInvoiceLineResponse> invoices
) {
}
