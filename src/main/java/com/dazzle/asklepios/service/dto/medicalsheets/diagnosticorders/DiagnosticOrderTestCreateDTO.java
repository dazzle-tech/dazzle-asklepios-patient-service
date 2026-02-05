// src/main/java/com/dazzle/asklepios/service/dto/medicalsheets/diagnosticorders/DiagnosticOrderTestCreateDTO.java
package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders;

import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestCreateDTO(

        @NotNull Long orderId,
        @NotNull Long testId,

        Long receivedDepartmentId,
        String reason,
        String notes,


        DiagnosticStatus processingStatus,

        Instant submitDate,

        TestType orderType
) implements Serializable { }
