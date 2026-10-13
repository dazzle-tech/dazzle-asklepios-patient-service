package com.dazzle.asklepios.integration.waseel.dto.claim;

public record ClaimReturnForResubmitResponse(
        int returnedCount,
        int skippedCount
) {}
