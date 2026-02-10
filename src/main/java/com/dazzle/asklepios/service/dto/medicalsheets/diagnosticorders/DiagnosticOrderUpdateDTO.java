package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosticOrderUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull Boolean isUrgent
) implements Serializable {
}
