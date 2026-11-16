package com.dazzle.asklepios.service.dailybriefing;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DailyBriefingCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Set<String> AUTH_DENIAL = Set.of("REJECTED", "DENIED");
    private static final Set<String> AUTH_CLOSED = Set.of("APPROVED", "CANCELLED", "PARTIAL", "NOT_REQUIRED");
    private static final Set<String> AUTH_PENDING = Set.of(
            "PENDING", "PENDING_APPROVAL", "DRAFT", "SUBMITTED", "SUBMITTING", "NEW", "IN_PROGRESS", "QUEUED"
    );
    private static final Set<AppointmentStatus> NOT_ON_BOOK = EnumSet.of(AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED);

    private DailyBriefingCalculator() {
    }

    public record ComparisonResult(
            BigDecimal baseline,
            BigDecimal current,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            String percentageOmittedReason
    ) {
    }

    public record RateResult(BigDecimal rate, String omittedReason) {
    }

    public record PatientSplit(long uniquePatients, long newPatients, long followUpPatients, long otherPatients) {
    }

    public record Conflict(Long practitionerId, Long firstAppointmentId, Long secondAppointmentId) {
    }

    public record HourLoad(int hour, long count) {
    }

    public static boolean isCompleted(TreatmentStatus status) {
        return status == TreatmentStatus.COMPLETED || status == TreatmentStatus.CLOSED || status == TreatmentStatus.DISCHARGED;
    }

    public static boolean isCancelled(TreatmentStatus status) {
        return status == TreatmentStatus.CANCELLED;
    }

    public static boolean countsAsEligibleAppointment(AppointmentStatus status) {
        return status != null && !NOT_ON_BOOK.contains(status);
    }

    public static ComparisonResult compare(Long current, Long baseline) {
        if (current == null || baseline == null) {
            return new ComparisonResult(decimal(baseline), decimal(current), null, null, "VALUE_MISSING");
        }
        return compare(BigDecimal.valueOf(current), BigDecimal.valueOf(baseline));
    }

    public static ComparisonResult compare(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null) {
            return new ComparisonResult(baseline, current, null, null, "VALUE_MISSING");
        }
        BigDecimal absolute = current.subtract(baseline);
        if (baseline.compareTo(BigDecimal.ZERO) == 0) {
            return new ComparisonResult(baseline, current, absolute, null, "BASELINE_ZERO");
        }
        BigDecimal percentage = absolute.multiply(HUNDRED).divide(baseline, 2, RoundingMode.HALF_UP);
        return new ComparisonResult(baseline, current, absolute, percentage, null);
    }

    public static RateResult rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return new RateResult(null, "BASELINE_ZERO");
        }
        if (numerator < 0 || numerator > denominator) {
            return new RateResult(null, "DATA_INCONSISTENT");
        }
        BigDecimal value = BigDecimal.valueOf(numerator).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
        return new RateResult(value, null);
    }

    public static BigDecimal netBilling(BigDecimal gross, BigDecimal discount, BigDecimal exemption, BigDecimal creditNotes) {
        return zero(gross).subtract(zero(discount)).subtract(zero(exemption)).subtract(zero(creditNotes));
    }

    public static PatientSplit classifyPatients(List<DailyBriefingFacts.EncounterRow> completed) {
        Map<Long, EnumSet<EncounterReason>> reasons = new LinkedHashMap<>();
        for (DailyBriefingFacts.EncounterRow row : completed) {
            if (row.patientId() == null) {
                continue;
            }
            EnumSet<EncounterReason> set = reasons.computeIfAbsent(row.patientId(), id -> EnumSet.noneOf(EncounterReason.class));
            if (row.reason() != null) {
                set.add(row.reason());
            }
        }
        long news = 0;
        long followUp = 0;
        long other = 0;
        for (EnumSet<EncounterReason> set : reasons.values()) {
            if (set.contains(EncounterReason.FIRST_TIME_VISIT)) {
                news++;
            } else if (set.contains(EncounterReason.FOLLOW_UP)) {
                followUp++;
            } else {
                other++;
            }
        }
        return new PatientSplit(reasons.size(), news, followUp, other);
    }

    public static List<Conflict> conflicts(List<DailyBriefingFacts.AppointmentRow> booked) {
        Map<Long, List<DailyBriefingFacts.AppointmentRow>> byPractitioner = new LinkedHashMap<>();
        for (DailyBriefingFacts.AppointmentRow row : booked) {
            if (row.practitionerId() == null || row.start() == null || row.end() == null) {
                continue;
            }
            if (!countsAsEligibleAppointment(row.status())) {
                continue;
            }
            byPractitioner.computeIfAbsent(row.practitionerId(), id -> new ArrayList<>()).add(row);
        }
        List<Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<Long, List<DailyBriefingFacts.AppointmentRow>> entry : byPractitioner.entrySet()) {
            List<DailyBriefingFacts.AppointmentRow> rows = entry.getValue().stream()
                    .sorted(Comparator.comparing(DailyBriefingFacts.AppointmentRow::start))
                    .toList();
            for (int i = 0; i < rows.size(); i++) {
                for (int j = i + 1; j < rows.size(); j++) {
                    if (!rows.get(j).start().isBefore(rows.get(i).end())) {
                        break;
                    }
                    conflicts.add(new Conflict(entry.getKey(), rows.get(i).id(), rows.get(j).id()));
                }
            }
        }
        return conflicts;
    }

    public static String session(int hour) {
        if (hour >= 6 && hour < 12) {
            return "MORNING";
        }
        if (hour >= 12 && hour < 17) {
            return "AFTERNOON";
        }
        if (hour >= 17 && hour < 22) {
            return "EVENING";
        }
        return "NIGHT";
    }

    public static List<HourLoad> busiestHours(List<Instant> starts, ZoneId zone, int limit) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Instant start : starts) {
            if (start == null) {
                continue;
            }
            int hour = start.atZone(zone).getHour();
            counts.merge(hour, 1L, Long::sum);
        }
        List<HourLoad> ranked = counts.entrySet().stream()
                .map(entry -> new HourLoad(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(HourLoad::count).reversed().thenComparingInt(HourLoad::hour))
                .toList();
        return ranked.stream().limit(Math.max(limit, 0)).toList();
    }

    public static String agingBucket(LocalDate orderedOn, LocalDate today) {
        if (orderedOn == null || today == null) {
            return "UNKNOWN";
        }
        long days = ChronoUnit.DAYS.between(orderedOn, today);
        if (days < 0) {
            return "FUTURE";
        }
        if (days <= 2) {
            return "0_2";
        }
        if (days <= 7) {
            return "3_7";
        }
        if (days <= 14) {
            return "8_14";
        }
        return "15_PLUS";
    }

    public static boolean isAuthorizationDenial(String status, String outcome) {
        return AUTH_DENIAL.contains(normalize(status)) || AUTH_DENIAL.contains(normalize(outcome));
    }

    public static boolean isClassifiedPendingAuthorization(String status, String outcome) {
        return isOpenAuthorization(status, outcome) && AUTH_PENDING.contains(normalize(status));
    }

    public static boolean isUnclassifiedOpenAuthorization(String status, String outcome) {
        return isOpenAuthorization(status, outcome) && !AUTH_PENDING.contains(normalize(status));
    }

    public static Long ageMinutes(Instant started, LocalDate date, LocalTime time, ZoneId zone, Instant now) {
        Instant start = started;
        if (start == null && date != null) {
            LocalTime clock = time == null ? LocalTime.MIN : time;
            start = date.atTime(clock).atZone(zone).toInstant();
        }
        if (start == null || now == null || now.isBefore(start)) {
            return null;
        }
        return Duration.between(start, now).toMinutes();
    }

    public static Long waitMinutes(Instant checkedIn, Instant started) {
        if (checkedIn == null || started == null || started.isBefore(checkedIn)) {
            return null;
        }
        return Duration.between(checkedIn, started).toMinutes();
    }

    public static BigDecimal median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        if (size % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(size / 2));
        }
        return BigDecimal.valueOf(sorted.get(size / 2 - 1))
                .add(BigDecimal.valueOf(sorted.get(size / 2)))
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal hoursBetween(Instant start, Instant end) {
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        return BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    public static long sum(List<DailyBriefingFacts.StatusCount> rows, java.util.function.Predicate<String> status) {
        long total = 0;
        if (rows == null) {
            return total;
        }
        for (DailyBriefingFacts.StatusCount row : rows) {
            if (status.test(row.status())) {
                total += row.count();
            }
        }
        return total;
    }

    private static boolean isOpenAuthorization(String status, String outcome) {
        if (isAuthorizationDenial(status, outcome)) {
            return false;
        }
        return !AUTH_CLOSED.contains(normalize(status));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal decimal(Long value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
