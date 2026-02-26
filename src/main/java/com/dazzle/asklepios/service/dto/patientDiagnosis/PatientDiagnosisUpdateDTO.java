package com.dazzle.asklepios.service.dto.patientDiagnosis;

import com.dazzle.asklepios.domain.enumeration.DiagnosisType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDiagnosisUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        Long diagnosisId,

        @NotNull
        DiagnosisType type,

        @NotNull
        Boolean suspected,

        @NotNull
        Boolean major

) implements Serializable {
}
