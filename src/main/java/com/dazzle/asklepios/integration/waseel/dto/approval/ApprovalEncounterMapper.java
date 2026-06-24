package com.dazzle.asklepios.integration.waseel.dto.approval;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ApprovalEncounterMapper {

    public WaseelApprovalEncounter toWaseelEncounter(PatientEncounter encounter, String providerId) {
        if (encounter == null) {
            throw new BadRequestAlertException(
                    "Encounter is required",
                    "preAuthorization",
                    "encounter.required"
            );
        }

        Long providerNphiesId = toLong(providerId);

        return new WaseelApprovalEncounter(
                "planned",
                "HH",
                "acute-care",
                encounter.getEncounterDate() != null
                        ? encounter.getEncounterDate()
                        : LocalDate.now(),
                "ICSE",
                providerNphiesId,
                providerNphiesId,
                null,
                ""
        );
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Long.valueOf(value.trim());
    }
}