package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimSubmissionResponse;

import java.util.List;

public record ClaimSettlementSubmitResponse(
        ClaimSettlementGroupSummaryResponse statement,
        int submittedCount,
        int releasedThisSubmit,
        String message,
        List<ClaimSubmissionResponse> claims
) {
}
