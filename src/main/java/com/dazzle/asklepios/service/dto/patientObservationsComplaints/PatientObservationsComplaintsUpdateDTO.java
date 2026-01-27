package com.dazzle.asklepios.service.dto.patientObservationsComplaints;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientObservationsComplaintsUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        String functionalStatus,

        @NotEmpty
        String reasonOfVisit,

        String cognitiveCheck,

        @NotNull
        Boolean isActive

) implements Serializable {
}
