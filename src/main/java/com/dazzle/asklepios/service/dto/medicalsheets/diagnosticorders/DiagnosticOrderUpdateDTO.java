package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long encounterId,
        String status,
        Boolean saveDraft,
        String submittedBy,
        Instant submittedDate,
        Boolean isUrgent,
        String labStatus,
        String radStatus
) implements Serializable {}
