package com.dazzle.asklepios.service.dailybriefing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record BriefingContext(
        String language,
        String facilityName,
        Long facilityId,
        Long departmentId,
        Long practitionerId,
        String specialty,
        LocalDate reportDate,
        String timeZone,
        String timeZoneSource,
        LocalDate currentDay,
        LocalDate previousDay,
        LocalDate comparisonDay,
        Instant refreshedAt,
        Instant occupancyAt,
        boolean currentDayPartial,
        ZoneId zone,
        int openEncounterHours,
        int noShowRatePercent,
        int waitingMinutes,
        int turnaroundMinutes,
        String permissionNote
) {
}
