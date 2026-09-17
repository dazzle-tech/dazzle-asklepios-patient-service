package com.dazzle.asklepios.service.dto.patientObservationsComplaints;

import com.dazzle.asklepios.domain.enumeration.BloodGroup;
import com.dazzle.asklepios.domain.enumeration.ModeOfArrival;
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

        String patientConditions,

        String functionalStatus,

        @NotEmpty
        String reasonOfVisit,

        ModeOfArrival modeOfArrival,

        @NotNull
        Boolean byPatient,

        Long sourceOfInformation,

        String cognitiveCheck,

        BloodGroup bloodGroup,

        @NotNull
        Boolean isActive

) implements Serializable {
}