package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PreAuthorizationSearchResponse(
        Long approvalRequestId,
        Long approvalResponseId,
        String payerNphiesId,
        String memberCardId,
        String insurer,
        BigDecimal paymentAmount,
        String claimResourceId,
        String outcome,
        String status,
        String disposition,
        String period,
        OffsetDateTime preAuthStartDate,
        OffsetDateTime preAuthEndDate,
        String processNotes,
        String preAuthRefNo,
        Long providertransactionlogId,
        OffsetDateTime transactionLogDate,
        String cancelStatus,
        String cancelResponseReason,
        List<Object> item,
        List<Object> diagnosis,
        List<Object> careTeam,
        List<Object> supportingInfo,
        List<Object> errors
) {}