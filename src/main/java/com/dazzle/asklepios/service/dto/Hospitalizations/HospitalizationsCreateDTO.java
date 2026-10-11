package com.dazzle.asklepios.service.dto.Hospitalizations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HospitalizationsCreateDTO(

        @NotNull
        Long patientId,

        String facility,

        String reason,

        String admissionType,

        Date dateOfAdmission,

        Integer lengthOfStayDays,

        String outcomes,

        String medicalInterventionsPerformed,

        @NotNull
        Boolean patientIsFree,

        String freeText

) implements Serializable {
}