package com.dazzle.asklepios.service.dto.Hospitalizations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HospitalizationsCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        @NotBlank
        String facility,

        @NotNull
        @NotBlank
        String reason,

        String admissionType,

        @NotNull
        Date dateOfAdmission,

        Integer lengthOfStayDays,

        String outcomes,

        String medicalInterventionsPerformed

) implements Serializable {
}
