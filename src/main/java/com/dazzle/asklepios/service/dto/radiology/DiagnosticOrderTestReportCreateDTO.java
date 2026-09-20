package com.dazzle.asklepios.service.dto.radiology;

import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public record DiagnosticOrderTestReportCreateDTO(

        @NotNull Long orderTestId,

        String report,

        String radiologistInformation,

        String criticalFindings,

        String radiologistComments,

        Severity severity
) implements Serializable {
}
