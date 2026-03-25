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

        return getBestNormalRangeMatchForPatient(candidates, patient).orElse(null);
    }

    private Optional<NormalRangeMatchDTO> getBestNormalRangeMatchForPatient(List<NormalRangeMatchDTO> candidates, Patient patient) {
        String patientGender = toGenderString(patient.getSexAtBirth());
        LocalDate patientDateOfBirth = patient.getDateOfBirth();
        return candidates.stream()
                .filter(normalRange -> matchesGender(normalRange, patientGender))
                .filter(normalRange -> matchesAge(normalRange, patientDateOfBirth))
                .filter(normalRange -> matchesCondition(normalRange, patient))
                .max(Comparator
                        .comparingInt((NormalRangeMatchDTO normalRange) -> specificityScore(normalRange))
                        .thenComparingDouble(this::ageWindowWidthOrInfinity).reversed()
                        .thenComparingLong(normalRange -> normalRange.id() == null ? Long.MAX_VALUE : normalRange.id())
                );
    }

    private boolean matchesGender(NormalRangeMatchDTO normalRange, String patientGender) {
        if (normalRange.gender() == null || normalRange.gender().isBlank()) {
            return true;
        }
        if (patientGender == null) {
            return false;
        }
        return normalRange.gender().trim().equalsIgnoreCase(patientGender);
    }

    private boolean matchesAge(NormalRangeMatchDTO normalRange, LocalDate patientDateOfBirth) {
        boolean hasAnyAgeConstraint =
                normalRange.ageFrom() != null || normalRange.ageTo() != null || normalRange.ageFromUnit() != null || normalRange.ageToUnit() != null;

        if (!hasAnyAgeConstraint) {
            return true;
        }

        if (patientDateOfBirth == null) {
            return false;
        }

        Instant currentInstant = Instant.now();

        if (normalRange.ageFrom() != null) {
            AgeUnit ageFromUnit = Objects.requireNonNullElse(normalRange.ageFromUnit(), AgeUnit.YEARS);
            double patientAgeAtLowerBoundUnit = patientAgeInUnit(patientDateOfBirth, ageFromUnit, currentInstant);
            if (patientAgeAtLowerBoundUnit < normalRange.ageFrom()) {
                return false;
            }
        }

        if (normalRange.ageTo() != null) {
            AgeUnit ageToUnit = Objects.requireNonNullElse(normalRange.ageToUnit(), AgeUnit.YEARS);
            double patientAgeAtUpperBoundUnit = patientAgeInUnit(patientDateOfBirth, ageToUnit, currentInstant);
            if (patientAgeAtUpperBoundUnit > normalRange.ageTo()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Placeholder: patient domain does not include condition currently.
     * If you later add condition on Patient, implement strict comparison here.
     */
    private boolean matchesCondition(NormalRangeMatchDTO normalRange, Patient patient) {
        return normalRange.condition() == null;
    }

    private int specificityScore(NormalRangeMatchDTO normalRange) {
        int score = 0;

        if (normalRange.gender() != null && !normalRange.gender().isBlank()) score += 4;

        boolean hasLowerBound = normalRange.ageFrom() != null;
        boolean hasUpperBound = normalRange.ageTo() != null;
        boolean hasAnyAge = hasLowerBound || hasUpperBound || normalRange.ageFromUnit() != null || normalRange.ageToUnit() != null;
        if (hasAnyAge) score += 2;

        if (normalRange.condition() != null) score += 1;

        return score;
    }

    private double ageWindowWidthOrInfinity(NormalRangeMatchDTO normalRange) {
        if (normalRange.ageFrom() == null || normalRange.ageTo() == null) {
            return Double.POSITIVE_INFINITY;
        }
        AgeUnit fromUnit = Objects.requireNonNullElse(normalRange.ageFromUnit(), AgeUnit.YEARS);
        AgeUnit toUnit = Objects.requireNonNullElse(normalRange.ageToUnit(), AgeUnit.YEARS);

        double fromDays = toDays(normalRange.ageFrom(), fromUnit);
        double toDays = toDays(normalRange.ageTo(), toUnit);
        return Math.abs(toDays - fromDays);
    }

    private double patientAgeInUnit(LocalDate dateOfBirth, AgeUnit unit, Instant nowInstant) {
        LocalDate currentDate = nowInstant.atZone(ZoneId.systemDefault()).toLocalDate();

        return switch (unit) {
            case YEARS -> ChronoUnit.YEARS.between(dateOfBirth, currentDate);
            case MONTHS -> ChronoUnit.MONTHS.between(dateOfBirth, currentDate);
            case WEEKS -> ChronoUnit.WEEKS.between(dateOfBirth, currentDate);
            case DAYS -> ChronoUnit.DAYS.between(dateOfBirth, currentDate);
            case HOURS -> {
                long ageInHours = ChronoUnit.HOURS.between(dateOfBirth.atStartOfDay(ZoneId.systemDefault()).toInstant(), nowInstant);
                yield ageInHours;
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
            return TestResultMarker.UNKNOWN;
        }

        return switch (resultType) {
            case NUMBER -> calculateNumber(resultValueNumber, normalRange);
            case LOV -> calculateLov(resultValueText, normalRange);
        };
    }

    private static TestResultMarker calculateNumber(BigDecimal resultValueNumber, NormalRangeMatchDTO normalRange) {
        if (resultValueNumber == null) {
            throw new BadRequestAlertException(
                    "Numeric result is required for NUMBER profile",
                    "diagnostic_order_test_results",
                    "result.number.required"
            );
        }

        double numericResultValue = resultValueNumber.doubleValue();

        if (Boolean.TRUE.equals(normalRange.criticalValue())) {
            Double criticalLowerThreshold = normalRange.criticalValueLessThan();
            if (criticalLowerThreshold != null && numericResultValue < criticalLowerThreshold) return TestResultMarker.CRITICAL_LOWER;

            Double criticalUpperThreshold = normalRange.criticalValueMoreThan();
            if (criticalUpperThreshold != null && numericResultValue > criticalUpperThreshold) return TestResultMarker.CRITICAL_UPPER;
        }

        Double normalLowerLimit = normalRange.rangeFrom();
        if (normalLowerLimit != null && numericResultValue < normalLowerLimit) return TestResultMarker.LOWER_LIMIT;

        Double normalUpperLimit = normalRange.rangeTo();
        if (normalUpperLimit != null && numericResultValue > normalUpperLimit) return TestResultMarker.UPPER_LIMIT;

        return TestResultMarker.NORMAL_MARKER;
    }

    private static TestResultMarker calculateLov(String resultValueText, NormalRangeMatchDTO normalRange) {
        if (resultValueText == null || resultValueText.isBlank()) {
            throw new BadRequestAlertException(
                    "Text/LOV result is required for LOV profile",
                    "diagnostic_order_test_results",
                    "result.lov.required"
            );
        }

        String normalizedResultValue = resultValueText.trim();

        if (normalRange.resultLov() != null && !normalRange.resultLov().isBlank()) {
            return normalizedResultValue.equalsIgnoreCase(normalRange.resultLov().trim())
                    ? TestResultMarker.NORMAL_MARKER
                    : TestResultMarker.ABNORMAL_MARKER;
        }

        if (normalRange.lovKeys() != null && !normalRange.lovKeys().isEmpty()) {
            boolean matchesAllowedLovKey = normalRange.lovKeys().stream()
                    .anyMatch(allowedLovKey -> normalizedResultValue.equalsIgnoreCase(allowedLovKey.trim()));
            return matchesAllowedLovKey ? TestResultMarker.NORMAL_MARKER : TestResultMarker.ABNORMAL_MARKER;
        }


        return TestResultMarker.NORMAL_MARKER;
    }


}
