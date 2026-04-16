package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosticOrderTestCreateDTO(

        @NotNull Long orderId,
        @NotNull Long testId,

        Long receivedDepartmentId,
        String reason,
        String notes,
        @NotNull TestType orderType,
        Long icdDiagnosisId
) implements Serializable { }
