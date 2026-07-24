package com.dazzle.asklepios.service.dto.radiology;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public record DiagnosticOrderTestReportCreateDTO(

        @NotNull Long orderTestId,

        String report,

        @Size(max = 50) String severity
) implements Serializable {
}
