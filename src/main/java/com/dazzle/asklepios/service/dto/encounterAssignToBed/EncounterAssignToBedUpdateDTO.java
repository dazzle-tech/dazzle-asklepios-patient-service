package com.dazzle.asklepios.service.dto.encounterAssignToBed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterAssignToBedUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long encounterId,

        @NotNull
        Long patientId,

        @NotNull
        Long roomId,

        @NotNull
        Long bedId,

        @NotNull
        Long departmentId,

        String admissionReason

) implements Serializable {
}