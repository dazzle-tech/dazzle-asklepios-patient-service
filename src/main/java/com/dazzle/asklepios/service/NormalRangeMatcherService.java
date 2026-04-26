package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.SetupServiceClient;
import com.dazzle.asklepios.client.setup.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.AgeUnit;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.NormalRangeType;
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
import java.time.ZonedDateTime;
import java.util.Comparator;
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
     * <p>
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

    private int ageSpecificityScore(NormalRangeMatchDTO normalRange) {
        boolean hasFrom = normalRange.ageFrom() != null;
        boolean hasTo = normalRange.ageTo() != null;

        if (hasFrom && hasTo) return 2;
        if (hasFrom || hasTo) return 1;
        return 0;
    }

    private Optional<NormalRangeMatchDTO> getBestNormalRangeMatchForPatient(List<NormalRangeMatchDTO> candidates, Patient patient) {
        String patientGender = toGenderString(patient.getSexAtBirth());
        LocalDate patientDateOfBirth = patient.getDateOfBirth();
        return candidates.stream()
                .filter(normalRange -> matchesGender(normalRange, patientGender))
                .filter(normalRange -> matchesAge(normalRange, patientDateOfBirth))
                .filter(normalRange -> matchesCondition(normalRange, patient))
                .sorted(
                        Comparator
                                .comparingInt((NormalRangeMatchDTO normalRange) -> specificityScore(normalRange))
                                .thenComparingInt(this::ageSpecificityScore)
                                .thenComparing(normalRange -> normalRange.id() == null ? Long.MAX_VALUE : normalRange.id())
                                .reversed()
                )
                .findFirst();
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
                normalRange.ageFrom() != null || normalRange.ageTo() != null
                        || normalRange.ageFromUnit() != null || normalRange.ageToUnit() != null;

        if (!hasAnyAgeConstraint) {
            return true;
        }

        if (patientDateOfBirth == null) {
            return false;
        }

        return isPatientWithinAgeRange(patientDateOfBirth, normalRange, Instant.now());
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
        return Math.abs(normalRange.ageTo() - normalRange.ageFrom());
    }

    private boolean isPatientWithinAgeRange(
            LocalDate dateOfBirth,
            NormalRangeMatchDTO range,
            Instant nowInstant
    ) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = nowInstant.atZone(zone);
        ZonedDateTime dob = dateOfBirth.atStartOfDay(zone);

        Double ageFrom = range.ageFrom();
        Double ageTo = range.ageTo();

        AgeUnit fromUnit = Objects.requireNonNullElse(range.ageFromUnit(), AgeUnit.YEARS);
        AgeUnit toUnit = Objects.requireNonNullElse(range.ageToUnit(), AgeUnit.YEARS);

        // minimum age
        if (ageFrom != null) {
            ZonedDateTime latestAllowedDob = subtractAge(now, ageFrom, fromUnit);
            if (dob.isAfter(latestAllowedDob)) {
                return false;
            }
        }

        // maximum age
        if (ageTo != null) {
            ZonedDateTime earliestAllowedDob = subtractAge(now, ageTo, toUnit);
            if (dob.isBefore(earliestAllowedDob)) {
                return false;
            }
        }

        return true;
    }

    private ZonedDateTime subtractAge(ZonedDateTime dateTime, Double value, AgeUnit unit) {
        if (value == null) return dateTime;

        long whole = value.longValue();
        double fraction = value - whole;

        ZonedDateTime result = switch (unit) {
            case YEARS -> dateTime.minusYears(whole);
            case MONTHS -> dateTime.minusMonths(whole);
            case WEEKS -> dateTime.minusWeeks(whole);
            case DAYS -> dateTime.minusDays(whole);
            case HOURS -> dateTime.minusHours(whole);
        };

        if (fraction > 0) {
            result = switch (unit) {
                case YEARS -> result.minusDays(Math.round(fraction * result.toLocalDate().lengthOfYear()));
                case MONTHS -> result.minusDays(Math.round(fraction * result.toLocalDate().lengthOfMonth()));
                case WEEKS -> result.minusHours(Math.round(fraction * 7 * 24));
                case DAYS -> result.minusHours(Math.round(fraction * 24));
                case HOURS -> result.minusMinutes(Math.round(fraction * 60));
            };
        }

        return result;
    }

    private String toGenderString(Gender gender) {
        if (gender == null) return null;
        return gender.name().toLowerCase(Locale.ROOT);
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
            if (criticalLowerThreshold != null && numericResultValue < criticalLowerThreshold) {
                return TestResultMarker.CRITICAL_LOWER;
            }

            Double criticalUpperThreshold = normalRange.criticalValueMoreThan();
            if (criticalUpperThreshold != null && numericResultValue > criticalUpperThreshold) {
                return TestResultMarker.CRITICAL_UPPER;
            }
        }

        NormalRangeType normalRangeType = normalRange.normalRangeType();

        if (normalRangeType == null) {
            normalRangeType = NormalRangeType.RANGE;
        }

        return switch (normalRangeType) {
            case RANGE -> {
                Double normalLowerLimit = normalRange.rangeFrom();
                if (normalLowerLimit != null && numericResultValue < normalLowerLimit) {
                    yield TestResultMarker.LOWER_LIMIT;
                }

                Double normalUpperLimit = normalRange.rangeTo();
                if (normalUpperLimit != null && numericResultValue > normalUpperLimit) {
                    yield TestResultMarker.UPPER_LIMIT;
                }

                yield TestResultMarker.NORMAL_MARKER;
            }

            case LESS_THAN -> {
                Double upperLimit = normalRange.rangeTo();
                if (upperLimit != null && numericResultValue > upperLimit) {
                    yield TestResultMarker.UPPER_LIMIT;
                }

                yield TestResultMarker.NORMAL_MARKER;
            }

            case MORE_THAN -> {
                Double lowerLimit = normalRange.rangeFrom();
                if (lowerLimit != null && numericResultValue < lowerLimit) {
                    yield TestResultMarker.LOWER_LIMIT;
                }

                yield TestResultMarker.NORMAL_MARKER;
            }
        };
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
