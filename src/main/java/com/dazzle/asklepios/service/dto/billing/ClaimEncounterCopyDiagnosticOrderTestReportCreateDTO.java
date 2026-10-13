package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.validation.constraints.NotNull;

public record ClaimEncounterCopyDiagnosticOrderTestReportCreateDTO(
        @NotNull Long orderTestId,
        String report,
        String radiologistInformation,
        String criticalFindings,
        String radiologistComments,
        Severity severity
) {
}
