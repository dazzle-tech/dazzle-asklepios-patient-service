package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.DischargeType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientEncounterDischargeDTO(

        @NotNull
        Long encounterId,

        @NotNull
        DischargeType dischargeType,

        @NotNull
        LocalDateTime dischargeAt

) implements Serializable {
}
