package com.dazzle.asklepios.integration.waseel.dto.approval;

public record PreAuthorizationInfoDTO(
        String dateOrdered,
        Long payeeId,
        String payeeType,
        String type,
        String subType,
        String prescription,
        String eligibilityOfflineDate,
        String eligibilityOfflineId,
        String eligibilityResponseId,
        String eligibilityResponseUrl,
        Long episodeId
) {}