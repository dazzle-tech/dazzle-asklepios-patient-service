package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests;

import com.dazzle.asklepios.domain.enumeration.TestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public record DiagnosticTestRequestCreateDTO(
        @NotNull TestType type,
        @NotBlank String name,
        @NotBlank  String indication,
        @NotNull  Long fromDepartmentId,
        @NotNull Long fromFacilityId
) {}
