package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientEncounterCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long facilityId,

        @NotNull
        Long departmentId,

        Long practitionerId,

        @NotNull
        Long appointmentId,

        @NotNull
        EncounterType encounterType,

        @NotNull
        EncounterReason encounterReason,

        Long followUpEncounterId,

        @NotNull
        EncounterPriority priorityLevel,

        String originType,

        String originName,

        String notes,

        LocalDate encounterDate,

        @JsonAlias("status")
        TreatmentStatus treatmentStatus,

        String chiefComplaint

) implements Serializable {
}
