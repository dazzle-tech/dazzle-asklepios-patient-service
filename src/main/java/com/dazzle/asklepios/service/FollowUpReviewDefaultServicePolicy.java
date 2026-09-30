package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPayments;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPaymentsRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Follow-up visits created within 14 calendar days of the previous visit
 * are treated as reviews and must not receive default services.
 * When that previous visit is completed, the follow-up keeps the same
 * Coverage Type and selected patient insurance shown on the visit form.
 */
@Service
public class FollowUpReviewDefaultServicePolicy {

    public static final int REVIEW_WINDOW_DAYS = 14;

    private static final Set<TreatmentStatus> COMPLETED_VISIT_STATUSES =
            EnumSet.of(TreatmentStatus.COMPLETED, TreatmentStatus.DISCHARGED);

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientPaymentsRepository patientPaymentsRepository;

    public FollowUpReviewDefaultServicePolicy(
            PatientEncounterRepository patientEncounterRepository,
            PatientPaymentsRepository patientPaymentsRepository
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientPaymentsRepository = patientPaymentsRepository;
    }

    public boolean shouldSkipDefaultServices(PatientEncounter encounter) {
        return previousVisitWithinReviewWindow(encounter) != null;
    }

    /**
     * Coverage Type and patient insurance from the selected completed visit,
     * when this follow-up is inside the 14-day window.
     * Uses the values saved on that visit, then its latest payment.
     */
    public Optional<InheritedVisitCoverage> coverageMatchingCompletedVisit(
            PatientEncounter encounter
    ) {
        PatientEncounter previousVisit = previousVisitWithinReviewWindow(encounter);
        if (previousVisit == null
                || !COMPLETED_VISIT_STATUSES.contains(previousVisit.getStatus())) {
            return Optional.empty();
        }

        return coverageShownOnVisit(previousVisit);
    }

    private Optional<InheritedVisitCoverage> coverageShownOnVisit(PatientEncounter visit) {
        if (visit.getCoverageType() == BillingCoverageType.SELF_PAY) {
            return Optional.of(new InheritedVisitCoverage(
                    visit.getId(),
                    BillingCoverageType.SELF_PAY,
                    null
            ));
        }

        if (visit.getCoverageType() == BillingCoverageType.INSURANCE
                && visit.getPatientInsuranceId() != null) {
            return Optional.of(new InheritedVisitCoverage(
                    visit.getId(),
                    BillingCoverageType.INSURANCE,
                    visit.getPatientInsuranceId()
            ));
        }

        return patientPaymentsRepository
                .findFirstByEncounterIdOrderByIdDesc(visit.getId())
                .flatMap(payment -> coverageFromPayment(visit.getId(), payment));
    }

    private Optional<InheritedVisitCoverage> coverageFromPayment(
            Long encounterId,
            PatientPayments payment
    ) {
        if (payment == null || payment.getPaymentTypes() == null) {
            return Optional.empty();
        }

        if (payment.getPaymentTypes() == PaymentTypes.INSURANCE_PLAN
                && payment.getPlan() != null
                && payment.getPlan().getId() != null) {
            return Optional.of(new InheritedVisitCoverage(
                    encounterId,
                    BillingCoverageType.INSURANCE,
                    payment.getPlan().getId()
            ));
        }

        if (payment.getPaymentTypes() == PaymentTypes.CASH) {
            return Optional.of(new InheritedVisitCoverage(
                    encounterId,
                    BillingCoverageType.SELF_PAY,
                    null
            ));
        }

        return Optional.empty();
    }

    private PatientEncounter previousVisitWithinReviewWindow(PatientEncounter encounter) {
        if (encounter == null
                || encounter.getEncounterReason() != EncounterReason.FOLLOW_UP
                || encounter.getFollowUpEncounter() == null
                || encounter.getFollowUpEncounter().getId() == null) {
            return null;
        }

        PatientEncounter previousVisit =
                patientEncounterRepository
                        .findById(encounter.getFollowUpEncounter().getId())
                        .orElse(null);

        if (previousVisit == null || previousVisit.getCreatedDate() == null) {
            return null;
        }

        Instant currentCreated =
                encounter.getCreatedDate() != null
                        ? encounter.getCreatedDate()
                        : Instant.now();

        ZoneId zone = ZoneId.systemDefault();
        LocalDate previousCreatedDate =
                previousVisit.getCreatedDate().atZone(zone).toLocalDate();
        LocalDate currentCreatedDate =
                currentCreated.atZone(zone).toLocalDate();

        long daysBetween =
                ChronoUnit.DAYS.between(previousCreatedDate, currentCreatedDate);

        if (daysBetween < 0 || daysBetween > REVIEW_WINDOW_DAYS) {
            return null;
        }

        return previousVisit;
    }

    public record InheritedVisitCoverage(
            Long previousEncounterId,
            BillingCoverageType coverageType,
            Long patientInsuranceId
    ) {
    }
}
