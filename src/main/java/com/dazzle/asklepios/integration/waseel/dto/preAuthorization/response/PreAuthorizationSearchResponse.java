package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        @JsonProperty("item")
        @JsonAlias({"items", "approvalItems", "claimItems"})
        List<PreAuthorizationSearchItem> item,
        List<Object> diagnosis,
        List<Object> careTeam,
        List<Object> supportingInfo,
        List<Object> errors
) {}
