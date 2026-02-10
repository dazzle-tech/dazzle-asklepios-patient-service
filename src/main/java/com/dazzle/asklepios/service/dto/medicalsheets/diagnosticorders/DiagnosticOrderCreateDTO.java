package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosticOrderCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
       @NotNull DiagnosticStatus status,
        Boolean isUrgent,
        DiagnosticStatus labStatus,
        DiagnosticStatus radStatus,
        @NotNull Long fromDepartmentId,
        @NotNull Long fromFacilityId
) implements Serializable {
}
