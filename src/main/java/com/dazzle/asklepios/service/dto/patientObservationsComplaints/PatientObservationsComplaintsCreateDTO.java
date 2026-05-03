package com.dazzle.asklepios.service.dto.patientObservationsComplaints;

import com.dazzle.asklepios.domain.enumeration.BloodGroup;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientObservationsComplaintsCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        String patientConditions,

        String functionalStatus,

        @NotEmpty
        String reasonOfVisit,

        String cognitiveCheck,

        BloodGroup bloodGroup,

        @NotNull
        Boolean isActive

) implements Serializable {
}
