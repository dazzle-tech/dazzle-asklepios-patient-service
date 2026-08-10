package com.dazzle.asklepios.integration.waseel.dto.claim;

import java.util.List;

public record ClaimBatchSubmitResponse(
        String uploadName,
        Long uploadId,
        String outcome,
        String message,
        int submittedClaimCount,
        List<ClaimSubmissionResponse> claims
) {}
