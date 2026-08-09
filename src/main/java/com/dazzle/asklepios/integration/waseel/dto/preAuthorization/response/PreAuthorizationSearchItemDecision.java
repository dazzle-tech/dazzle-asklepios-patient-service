package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreAuthorizationSearchItemDecision(
        Long itemDecisionId,
        String status,
        Integer itemSequence
) {}
