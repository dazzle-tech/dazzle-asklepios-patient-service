package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientEncounterCreateDTO(

        @NotNull
        Long patientId,

        String encounterNumber,

        @NotNull
        Long facilityId,

        @NotNull
        Long departmentId,

        Long practitionerId,

        String appointmentId,

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

        Integer departmentDailySequenceNumber,

        LocalDate encounterDate,

        @NotNull
        EncounterStatus status,

        String chiefComplaint,

        @NotNull
        Boolean hasPrescription,

        @NotNull
        Boolean hasOrder,

        @NotNull
        Boolean isObserved

) implements Serializable {
}
