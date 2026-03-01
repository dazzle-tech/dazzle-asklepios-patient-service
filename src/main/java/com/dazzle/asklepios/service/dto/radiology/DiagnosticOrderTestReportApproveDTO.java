package com.dazzle.asklepios.service.dto.radiology;

import jakarta.validation.constraints.NotNull;

public record DiagnosticOrderTestReportApproveDTO(
        @NotNull Long orderTestId
) {}
