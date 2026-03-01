package com.dazzle.asklepios.service.dto.radiology;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record DiagnosticOrderTestReportReviewDTO(
        @NotNull Long orderTestId
) implements Serializable {}