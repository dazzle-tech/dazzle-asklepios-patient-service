package com.dazzle.asklepios.integration.waseel.dto.claim;

import java.util.List;

public record ClaimReturnForResubmitRequest(
        List<Long> claimIds
) {}
