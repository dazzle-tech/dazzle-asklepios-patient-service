package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record
DiagnosticOrderTestUpdateDTO(
        @NotNull Long id,
        @NotNull Long orderId,
        @NotNull Long testId,

        Long receivedDepartmentId,
        String reason,
        String notes,
        Long icdDiagnosisId


) implements Serializable { }
