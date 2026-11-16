package com.dazzle.asklepios.service.dailybriefing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record BriefingWindow(
        ZoneId zone,
        LocalDate currentDay,
        LocalDate previousDay,
        LocalDate comparisonDay,
        Instant currentStart,
        Instant currentEnd,
        Instant previousStart,
        Instant previousEnd,
        Instant comparisonStart,
        Instant comparisonEnd,
        Instant refreshedAt,
        Instant occupancyAt,
        int turnaroundMinutes,
        int openEncounterHours
) {
}
