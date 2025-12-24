package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long encounterId,
        String status,
        @NotNull Long orderId,
        @NotNull Long testId,
        Long receivedDepartmentId,
        String reason,
        String notes,
        String processingStatus,
        Instant submitDate,
        Instant acceptedDate,
        Instant rejectedDate,
        Instant patientArrivedDate,
        Instant readyDate,
        Instant approvedDate,
        String orderType,
        String acceptedBy,
        String rejectedBy,
        String rejectedReason,
        String patientArrivedNoteRad,
        String cancellationReason,
        Long fromDepartmentId,
        Long fromFacilityId,
        Long toFacilityId,
        Boolean isActive
) implements Serializable {}
