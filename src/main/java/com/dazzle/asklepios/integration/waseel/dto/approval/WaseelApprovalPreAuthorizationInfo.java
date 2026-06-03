package com.dazzle.asklepios.integration.waseel.dto.approval;

import java.time.LocalDate;

public record WaseelApprovalPreAuthorizationInfo(
        LocalDate dateOrdered,
        String payeeId,
        String payeeType,
        String type,
        String subType,
        String prescription,
        LocalDate eligibilityOfflineDate,
        String eligibilityOfflineId,
        String eligibilityResponseId,
        String eligibilityResponseUrl,
        String episodeId
) {}