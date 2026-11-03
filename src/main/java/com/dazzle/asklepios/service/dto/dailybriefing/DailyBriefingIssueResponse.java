package com.dazzle.asklepios.service.dto.dailybriefing;

import java.time.Instant;

public record DailyBriefingIssueResponse(
        Long id,
        String persistenceStatus,
        Instant storedAt,
        String recipient,
        boolean supersededPrevious,
        DailyBriefingResponse report
) {
}
