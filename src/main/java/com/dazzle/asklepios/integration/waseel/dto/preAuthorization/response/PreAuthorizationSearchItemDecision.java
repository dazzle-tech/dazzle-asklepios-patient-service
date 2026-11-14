package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The payer decision on one pre-authorization item. {@code claimItemDecisionCategories} carries the
 * amounts: "benefit" (approved amount), "eligible", "submitted", "copay" / "patientshare" (patient part)
 * and, on a rejected item, the reason code and description.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreAuthorizationSearchItemDecision(
        Long itemDecisionId,
        String status,
        Integer itemSequence,
        List<Category> claimItemDecisionCategories
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String category,
            BigDecimal categoryValue,
            String categoryCurrency,
            String reasonCode,
            String reasonDescription
    ) {
    }
}
