package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterLifecycleStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Validates clinical encounter state before insurance financial settlement.
 */
@Service
public class EncounterBillingGuardService {

    private static final String ENTITY_NAME = "encounterBillingGuard";

    private static final Set<TreatmentStatus> CLINICALLY_COMPLETED_TREATMENT_STATUSES =
            Set.of(
                    TreatmentStatus.CLOSED,
                    TreatmentStatus.DISCHARGED
            );

    public void requireClinicallyCompleteForFinancialSettlement(
            PatientEncounter encounter
    ) {
        if (encounter == null) {
            throw new BadRequestAlertException(
                    "Encounter is required.",
                    ENTITY_NAME,
                    "encounter.required"
            );
        }

        if (encounter.getEncounterStatus() == EncounterStatus.CLOSED) {
            return;
        }

        TreatmentStatus treatmentStatus = encounter.getTreatmentStatus();
        if (treatmentStatus != null
                && CLINICALLY_COMPLETED_TREATMENT_STATUSES.contains(
                treatmentStatus
        )) {
            return;
        }

        throw new BadRequestAlertException(
                "The encounter is still open and must be completed before "
                        + "financial settlement can continue.",
                ENTITY_NAME,
                "encounter.notClinicallyComplete"
        );
    }
}
