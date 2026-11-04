package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Severity;

public record ClaimEncounterCopyDiagnosticOrderTestReportUpdateDTO(
        String report,
        String radiologistInformation,
        String criticalFindings,
        String radiologistComments,
        Severity severity
) {
}
