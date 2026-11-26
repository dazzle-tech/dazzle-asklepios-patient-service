package com.dazzle.asklepios.service.dto.dailybriefing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyBriefingCasesResponse(
        String indicator,
        LocalDate reportDate,
        int returned,
        boolean truncated,
        String note,
        List<DailyBriefingCase> cases
) {
    public record DailyBriefingCase(
            Long caseId,
            String caseType,
            Long patientId,
            Long departmentId,
            Long practitionerId,
            String status,
            Instant occurredAt,
            BigDecimal amount,
            String currency,
            Long ageHours
    ) {
    }
}
