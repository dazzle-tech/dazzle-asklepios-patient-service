package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreAuthorizationSearchItem(
        @JsonAlias({"claimItemId", "waseelClaimItemId", "id", "ItemId", "ClaimItemId"})
        @JsonDeserialize(using = FlexibleLongDeserializer.class)
        Long itemId,
        Integer sequence,
        String type,
        String itemCode,
        String itemDescription,
        String nonStandardCode,
        String nonStandardDesc,
        Boolean isPackage,
        Boolean isMaternity,
        BigDecimal quantity,
        String quantityCode,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal factor,
        BigDecimal taxPercent,
        BigDecimal tax,
        BigDecimal patientSharePercent,
        BigDecimal patientShare,
        BigDecimal payerShare,
        BigDecimal net,
        String status,
        String decision,
        String reasonCodes,
        PreAuthorizationSearchItemDecision itemDecision
) {}
