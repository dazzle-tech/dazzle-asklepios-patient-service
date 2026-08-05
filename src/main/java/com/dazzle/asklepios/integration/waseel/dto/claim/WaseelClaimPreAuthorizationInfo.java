package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaseelClaimPreAuthorizationInfo(
        LocalDate dateOrdered,
        String type,
        String subType,
        Long payeeId,
        String payeeType,
        String eligibilityOfflineId,
        LocalDate eligibilityOfflineDate,
        String eligibilityResponseId,
        LocalDate billableStart,
        LocalDate billableEnd,
        String preAuthResponseId,
        String preAuthIdentifierUrl,
        String eligibilityResponseUrl,
        LocalDate preAuthOfflineDate,
        String episodeId,
        LocalDate accountingPeriod
) {}
