package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.SetupServiceClient;
import com.dazzle.asklepios.client.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.AgeUnit;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class NormalRangeMatcherService {

    private final SetupServiceClient setupServiceClient;
    private final PatientRepository patientRepository;

    public NormalRangeMatcherService(SetupServiceClient setupServiceClient, PatientRepository patientRepository) {
        this.setupServiceClient = setupServiceClient;
        this.patientRepository = patientRepository;
    }

    /**
     * Finds the best matching normal range for a given profile test and patient.
     * Matching rules:
     * - profileTestId must match (setup-service already scopes this)
     * - gender: null => general; otherwise must match patient sexAtBirth
     * - age: null bounds/units => not restricted; otherwise patient age must fall within bounds
     * - condition: null => general; otherwise must match patient condition (if applicable)
     * Selection:
     *
     * - prefer more specific records (gender specified, age specified, condition specified)
     * - tie-breaker: narrower age window (if both specified), then smallest id
     */
    public NormalRangeMatchDTO findBestNormalRange(Long profileTestId, Long patientId) {
        List<NormalRangeMatchDTO> candidates =
                setupServiceClient.findAllByProfileTestIdInternal(profileTestId);

        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "patients",
                        "Patient not found with id " + patientId
                ));

        return pickBest(candidates, patient).orElse(null);
    }

    private Optional<NormalRangeMatchDTO> pickBest(List<NormalRangeMatchDTO> candidates, Patient patient) {
        String patientGender = toGenderString(patient.getSexAtBirth());
        LocalDate dob = toLocalDate(patient.getDateOfBirth());

        return candidates.stream()
                .filter(r -> matchesGender(r, patientGender))
                .filter(r -> matchesAge(r, dob))
                .filter(r -> matchesCondition(r, patient))
                .max(Comparator
                        .comparingInt((NormalRangeMatchDTO r) -> specificityScore(r))
                        .thenComparingDouble(this::ageWindowWidthOrInfinity).reversed()
                        .thenComparingLong(r -> r.id() == null ? Long.MAX_VALUE : r.id())
                );
    }

    private boolean matchesGender(NormalRangeMatchDTO r, String patientGender) {
        if (r.gender() == null || r.gender().isBlank()) {
            return true;
        }
        if (patientGender == null) {
            return false;
        }
        return r.gender().trim().equalsIgnoreCase(patientGender);
    }

    private boolean matchesAge(NormalRangeMatchDTO r, LocalDate dob) {
        boolean hasAnyAgeConstraint =
                r.ageFrom() != null || r.ageTo() != null || r.ageFromUnit() != null || r.ageToUnit() != null;

        if (!hasAnyAgeConstraint) {
            return true;
        }

        if (dob == null) {
            return false;
        }

        Instant now = Instant.now();

        if (r.ageFrom() != null) {
            AgeUnit unit = Objects.requireNonNullElse(r.ageFromUnit(), AgeUnit.YEARS);
            double patientAge = patientAgeInUnit(dob, unit, now);
            if (patientAge < r.ageFrom()) {
                return false;
            }
        }

        if (r.ageTo() != null) {
            AgeUnit unit = Objects.requireNonNullElse(r.ageToUnit(), AgeUnit.YEARS);
            double patientAge = patientAgeInUnit(dob, unit, now);
            if (patientAge > r.ageTo()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Placeholder: patient domain does not include condition currently.
     * If you later add condition on Patient, implement strict comparison here.
     */
    private boolean matchesCondition(NormalRangeMatchDTO r, Patient patient) {
        return r.condition() == null;
    }

    private int specificityScore(NormalRangeMatchDTO r) {
        int score = 0;

        if (r.gender() != null && !r.gender().isBlank()) score += 4;

        boolean hasFrom = r.ageFrom() != null;
        boolean hasTo = r.ageTo() != null;
        boolean hasAnyAge = hasFrom || hasTo || r.ageFromUnit() != null || r.ageToUnit() != null;
        if (hasAnyAge) score += 2;

        if (r.condition() != null) score += 1;

        return score;
    }

    private double ageWindowWidthOrInfinity(NormalRangeMatchDTO r) {
        if (r.ageFrom() == null || r.ageTo() == null) {
            return Double.POSITIVE_INFINITY;
        }
        AgeUnit fromUnit = Objects.requireNonNullElse(r.ageFromUnit(), AgeUnit.YEARS);
        AgeUnit toUnit = Objects.requireNonNullElse(r.ageToUnit(), AgeUnit.YEARS);

        double fromDays = toDays(r.ageFrom(), fromUnit);
        double toDays = toDays(r.ageTo(), toUnit);
        return Math.abs(toDays - fromDays);
    }

    private double patientAgeInUnit(LocalDate dob, AgeUnit unit, Instant nowInstant) {
        LocalDate now = nowInstant.atZone(ZoneId.systemDefault()).toLocalDate();

        return switch (unit) {
            case YEARS -> ChronoUnit.YEARS.between(dob, now);
            case MONTHS -> ChronoUnit.MONTHS.between(dob, now);
            case WEEKS -> ChronoUnit.WEEKS.between(dob, now);
            case DAYS -> ChronoUnit.DAYS.between(dob, now);
            case HOURS -> {
                long hours = ChronoUnit.HOURS.between(dob.atStartOfDay(ZoneId.systemDefault()).toInstant(), nowInstant);
                yield hours;
            }
        };
    }

    private double toDays(Double value, AgeUnit unit) {
        if (value == null) return 0d;

        return switch (unit) {
            case YEARS -> value * 365d;
            case MONTHS -> value * 30d;
            case WEEKS -> value * 7d;
            case DAYS -> value;
            case HOURS -> value / 24d;
        };
    }

    private String toGenderString(Gender gender) {
        if (gender == null) return null;
        return gender.name().toLowerCase(Locale.ROOT);
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static TestResultMarker calculateMarker(
            TestResultType resultType,
            BigDecimal resultValueNumber,
            String resultValueText,
            NormalRangeMatchDTO normalRange
    ) {
        if (normalRange == null || resultType == null) {
            return TestResultMarker.NORMAL_MARKER;
        }

        return switch (resultType) {
            case NUMBER -> calculateNumber(resultValueNumber, normalRange);
            case LOV -> calculateLov(resultValueText, normalRange);
        };
    }

    private static TestResultMarker calculateNumber(BigDecimal resultValueNumber, NormalRangeMatchDTO normalRange) {
        if (resultValueNumber == null) {

            throw new IllegalArgumentException("Numeric result is required for NUMBER profile");
        }

        double v = resultValueNumber.doubleValue();

        if (Boolean.TRUE.equals(normalRange.criticalValue())) {
            Double lessThan = normalRange.criticalValueLessThan();
            if (lessThan != null && v < lessThan) return TestResultMarker.CRITICAL_LOWER;

            Double moreThan = normalRange.criticalValueMoreThan();
            if (moreThan != null && v > moreThan) return TestResultMarker.CRITICAL_UPPER;
        }

        Double from = normalRange.rangeFrom();
        if (from != null && v < from) return TestResultMarker.LOWER_LIMIT;

        Double to = normalRange.rangeTo();
        if (to != null && v > to) return TestResultMarker.UPPER_LIMIT;

        return TestResultMarker.NORMAL_MARKER;
    }

    private static TestResultMarker calculateLov(String resultValueText, NormalRangeMatchDTO normalRange) {
        if (resultValueText == null || resultValueText.isBlank()) {
            throw new IllegalArgumentException("Text/LOV result is required for LOV profile");
        }

        String v = resultValueText.trim();

        if (normalRange.resultLov() != null && !normalRange.resultLov().isBlank()) {
            return v.equalsIgnoreCase(normalRange.resultLov().trim())
                    ? TestResultMarker.NORMAL_MARKER
                    : TestResultMarker.ABNORMAL_MARKER;
        }

        if (normalRange.lovKeys() != null && !normalRange.lovKeys().isEmpty()) {
            boolean ok = normalRange.lovKeys().stream().anyMatch(k -> v.equalsIgnoreCase(k.trim()));
            return ok ? TestResultMarker.NORMAL_MARKER : TestResultMarker.ABNORMAL_MARKER;
        }


        return TestResultMarker.NORMAL_MARKER;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}

