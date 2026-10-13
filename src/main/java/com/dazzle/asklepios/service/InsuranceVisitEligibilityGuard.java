package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks insurance visit confirmation unless Waseel eligibility was checked
 * within the last 24 hours and the stored coverage is in force.
 */
@Service
@RequiredArgsConstructor
public class InsuranceVisitEligibilityGuard {

    static final Duration ELIGIBILITY_WINDOW = Duration.ofHours(24);

    private static final String ENTITY_NAME = "defaultServiceBilling";

    private static final Set<String> EXACT_NEGATIVE = Set.of(
            "false",
            "no",
            "0",
            "n",
            "inactive",
            "rejected",
            "error",
            "failed"
    );

    private static final Set<String> EXACT_POSITIVE = Set.of(
            "true",
            "yes",
            "1",
            "y",
            "eligible",
            "active",
            "in-force",
            "inforce",
            "in force"
    );

    private final WaseelEligibilityRequestRepository eligibilityRequestRepository;

    public void requireFreshInForce(PatientInsurance insurance, Long patientId) {
        if (insurance == null || insurance.getId() == null) {
            throw new BadRequestAlertException(
                    "Patient insurance is required for insurance coverage.",
                    ENTITY_NAME,
                    "patientInsurance.required"
            );
        }

        Instant checkedAt = resolveCheckedAt(insurance, patientId);
        if (!isWithinWindow(checkedAt)) {
            throw new BadRequestAlertException(
                    "Check eligibility within the last 24 hours, or change the coverage type, before confirming.",
                    ENTITY_NAME,
                    "eligibility.stale"
            );
        }

        if (isExpired(insurance)) {
            throw new BadRequestAlertException(
                    "This insurance is expired. Check eligibility or change the coverage type before confirming.",
                    ENTITY_NAME,
                    "eligibility.expired"
            );
        }

        if (!isInForce(insurance)) {
            throw new BadRequestAlertException(
                    "This insurance is not in force. Check eligibility again or change the coverage type before confirming.",
                    ENTITY_NAME,
                    "eligibility.notInForce"
            );
        }
    }

    private Instant resolveCheckedAt(PatientInsurance insurance, Long patientId) {
        Instant syncedAt = insurance.getLastEligibilitySyncedAt();
        if (patientId == null) {
            return syncedAt;
        }

        return eligibilityRequestRepository
                .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndRespondedAtIsNotNullOrderByRespondedAtDesc(
                        patientId,
                        insurance.getId(),
                        "SUCCESS"
                )
                .map(WaseelEligibilityRequest::getRespondedAt)
                .orElse(syncedAt);
    }

    private boolean isWithinWindow(Instant checkedAt) {
        if (checkedAt == null) {
            return false;
        }

        Instant now = Instant.now();
        return !checkedAt.isBefore(now.minus(ELIGIBILITY_WINDOW))
                && !checkedAt.isAfter(now.plus(Duration.ofMinutes(1)));
    }

    private boolean isExpired(PatientInsurance insurance) {
        LocalDate today = LocalDate.now();
        if (insurance.getExpirationDate() != null
                && insurance.getExpirationDate().isBefore(today)) {
            return true;
        }

        return insurance.getBenefitEndDate() != null
                && insurance.getBenefitEndDate().isBefore(today);
    }

    private boolean isInForce(PatientInsurance insurance) {
        String inforce = normalize(insurance.getInforce());
        String status = normalize(insurance.getEligibilityStatus());
        String siteEligibility = normalize(insurance.getSiteEligibility());

        if (isNegative(inforce) || isNegative(status) || isNegative(siteEligibility)) {
            return false;
        }

        return isPositive(inforce) || isPositive(status) || isPositive(siteEligibility);
    }

    private boolean isNegative(String value) {
        if (value.isEmpty()) {
            return false;
        }

        if (EXACT_NEGATIVE.contains(value)) {
            return true;
        }

        return value.contains("not-active")
                || value.contains("not active")
                || value.contains("not-eligible")
                || value.contains("not eligible")
                || value.contains("ineligible")
                || value.contains("not-covered")
                || value.contains("not covered")
                || value.contains("not in-force")
                || value.contains("not in force")
                || value.contains("not inforce")
                || value.contains("out-network")
                || value.contains("out of network");
    }

    private boolean isPositive(String value) {
        return EXACT_POSITIVE.contains(value);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}
