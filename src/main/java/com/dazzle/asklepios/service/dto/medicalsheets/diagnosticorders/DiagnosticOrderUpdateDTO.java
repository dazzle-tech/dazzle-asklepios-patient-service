package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long encounterId,
        DiagnosticStatus status,
        Boolean saveDraft,
        String submittedBy,
        Instant submittedDate,
        Boolean isUrgent,
        Long fromDepartmentId,
        Long fromFacilityId
) implements Serializable {}
