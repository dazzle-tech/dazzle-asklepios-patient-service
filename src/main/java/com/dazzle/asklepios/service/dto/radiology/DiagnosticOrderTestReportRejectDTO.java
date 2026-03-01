package com.dazzle.asklepios.service.dto.radiology;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public record DiagnosticOrderTestReportRejectDTO(
        @NotNull Long orderTestId,
        @Size(max = 500) String rejectedReason
) implements Serializable {}
