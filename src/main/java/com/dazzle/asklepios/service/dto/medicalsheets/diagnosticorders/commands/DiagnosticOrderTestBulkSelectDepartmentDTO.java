package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.commands;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DiagnosticOrderTestBulkSelectDepartmentDTO(
        @NotNull Long departmentId,
        @NotEmpty List<Long> testsIds
) {}
