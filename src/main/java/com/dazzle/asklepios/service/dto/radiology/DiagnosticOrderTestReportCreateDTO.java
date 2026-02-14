package com.dazzle.asklepios.service.dto.radiology;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestReportCreateDTO(

        @NotNull Long orderTestId,

        String report,

        @Size(max = 50) String severity
) implements Serializable {}
