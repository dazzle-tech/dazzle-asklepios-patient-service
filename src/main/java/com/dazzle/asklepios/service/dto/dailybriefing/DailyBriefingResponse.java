package com.dazzle.asklepios.service.dto.dailybriefing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyBriefingResponse(
        String language,
        AppliedFilters filters,
        HighlightThresholds thresholds,
        Issuance issuance,
        ExecutiveSummary executiveSummary,
        List<Indicator> keyIndicators,
        List<Alert> exceptions,
        List<ActionItem> actions,
        Section encounters,
        Section appointments,
        Section billing,
        Section insurance,
        Section waitingAndResults,
        Section completion,
        Section inpatient,
        Section comparisons,
        DataQuality dataQuality
) {

    public enum ValueState {
        VALUE,
        TRUE_ZERO,
        UNAVAILABLE
    }

    public enum Coverage {
        COMPLETE,
        PARTIAL,
        NOT_IN_SOURCE
    }

    public enum Drill {
        COMPLETED_ENCOUNTERS,
        CANCELLED_ENCOUNTERS,
        INCOMPLETE_ENCOUNTERS,
        TODAY_APPOINTMENTS,
        PREVIOUS_CANCELLATIONS,
        PREVIOUS_NO_SHOWS,
        SCHEDULE_CONFLICTS,
        BOOKED_WITHOUT_PRACTITIONER,
        PENDING_AUTHORIZATIONS,
        AUTHORIZATION_DENIALS,
        SERVICES_ON_HOLD,
        READY_CLAIMS,
        CLAIM_DENIALS,
        CLAIM_FAILURES,
        OVERDUE_ORDERS,
        OPEN_ENCOUNTERS,
        MISSING_CHIEF_COMPLAINT,
        UNBILLED_ENCOUNTERS,
        ADMISSIONS,
        DISCHARGES,
        TRANSFERS,
        AWAITING_BED,
        ED_VISITS
    }

    public record AppliedFilters(
            Long facilityId,
            String facilityName,
            Long departmentId,
            Long practitionerId,
            String specialty,
            String branch,
            String branchNote
    ) {
    }

    public record HighlightThresholds(
            int openEncounterHours,
            int noShowRatePercent,
            int waitingMinutes,
            int turnaroundMinutes,
            String targetNote
    ) {
    }

    public record Issuance(
            String status,
            Instant createdAt,
            LocalDate currentDay,
            LocalDate previousDay,
            LocalDate comparisonDay,
            String timeZone,
            String timeZoneSource,
            boolean previousDayComplete,
            boolean currentDayPartial,
            Instant occupancyAt,
            List<String> errors,
            String duplicatePolicy
    ) {
    }

    public record ExecutiveSummary(
            String narrative,
            List<String> significantChanges,
            List<String> priorities
    ) {
    }

    public record Indicator(
            String code,
            String name,
            String definition,
            String source,
            String period,
            Instant refreshedAt,
            ValueState valueState,
            Coverage coverage,
            Long count,
            BigDecimal amount,
            String currency,
            List<MoneyAmount> amounts,
            BigDecimal rate,
            String unit,
            String note,
            String unavailableReason,
            Comparison comparison,
            String drillDownPath
    ) {
    }

    public record MoneyAmount(String currency, BigDecimal amount, Long count, String label) {
    }

    public record Comparison(
            String baselinePeriod,
            BigDecimal baseline,
            BigDecimal current,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            String percentageOmittedReason
    ) {
    }

    public record Alert(
            String code,
            String priority,
            String category,
            String issue,
            String evidence,
            String impact,
            String recommendedAction,
            boolean requiresReview,
            boolean modifiesRecords,
            boolean sendsClinicalInstructions,
            boolean causeKnown,
            String cause,
            boolean ownerKnown,
            String owner,
            boolean targetDateKnown,
            LocalDate targetDate
    ) {
    }

    public record ActionItem(
            String alertCode,
            String issue,
            String evidence,
            String impact,
            String recommendedAction,
            boolean requiresReview,
            boolean modifiesRecords,
            boolean sendsClinicalInstructions,
            boolean causeKnown,
            String cause,
            boolean ownerKnown,
            String owner,
            boolean targetDateKnown,
            LocalDate targetDate
    ) {
    }

    public record Section(
            String code,
            String title,
            boolean included,
            String omissionReason,
            String definition,
            List<Indicator> indicators,
            List<Breakdown> breakdowns,
            List<String> notes
    ) {
    }

    public record Breakdown(
            String code,
            String title,
            Coverage coverage,
            String unavailableReason,
            List<DimensionRow> rows
    ) {
    }

    public record DimensionRow(String key, String label, long count, BigDecimal amount, String currency) {
    }

    public record DataQuality(
            String timeZone,
            String timeZoneSource,
            Instant refreshedAt,
            boolean previousDayComplete,
            boolean currentDayPartial,
            String calculationPolicy,
            String privacyPolicy,
            String criticalResultPolicy,
            List<String> failures,
            List<String> notes,
            String permissionNote
    ) {
    }
}
