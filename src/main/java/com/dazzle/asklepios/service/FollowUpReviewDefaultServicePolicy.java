package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Follow-up visits created within 14 calendar days of the previous visit
 * are treated as reviews and must not receive default services.
 */
@Service
public class FollowUpReviewDefaultServicePolicy {

    public static final int REVIEW_WINDOW_DAYS = 14;

    private final PatientEncounterRepository patientEncounterRepository;

    public FollowUpReviewDefaultServicePolicy(
            PatientEncounterRepository patientEncounterRepository
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
    }

    public boolean shouldSkipDefaultServices(PatientEncounter encounter) {
        if (encounter == null
                || encounter.getEncounterReason() != EncounterReason.FOLLOW_UP
                || encounter.getFollowUpEncounter() == null
                || encounter.getFollowUpEncounter().getId() == null) {
            return false;
        }

        PatientEncounter previousVisit =
                patientEncounterRepository
                        .findById(encounter.getFollowUpEncounter().getId())
                        .orElse(null);

        if (previousVisit == null || previousVisit.getCreatedDate() == null) {
            return false;
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

        return daysBetween >= 0 && daysBetween <= REVIEW_WINDOW_DAYS;
    }
}
