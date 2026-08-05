package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaseelClaimUploadRequest(
        String uploadName,
        Long uploadId,
        List<WaseelClaimRequest> claimRequestModels
) {}
