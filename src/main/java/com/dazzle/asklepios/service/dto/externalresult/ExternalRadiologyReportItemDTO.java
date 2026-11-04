package com.dazzle.asklepios.service.dto.externalresult;

import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExternalRadiologyReportItemDTO(
        @Size(max = 255) String testName,
        LocalDate reportDate,
        String report,
        Severity severity,
        String radiologistInformation,
        String criticalFindings,
        String radiologistComments,
        @Size(max = 255) String physicianName
) {
}
