// src/main/java/com/dazzle/asklepios/service/dto/medicalsheets/diagnosticorders/DiagnosticOrderTestUpdateDTO.java
package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record
DiagnosticOrderTestUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull Long orderId,
        @NotNull Long testId,

        Long receivedDepartmentId,
        String reason,
        String notes

) implements Serializable { }
