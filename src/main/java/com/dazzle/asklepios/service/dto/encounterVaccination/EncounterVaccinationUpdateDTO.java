package com.dazzle.asklepios.service.dto.encounterVaccination;

import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterVaccinationUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        Long vaccineId,

        @NotNull
        Long vaccineBrandId,

        @NotNull
        Long vaccineDoseId,

        String vaccineLotNumber,

        @NotNull
        Instant dateAdministered,

        @NotNull
        EncounterVaccinationStatus status,

        String administeredLocation,
        String administrationReactions,
        String externalFacilityName,
        String notes

) implements Serializable {
}
