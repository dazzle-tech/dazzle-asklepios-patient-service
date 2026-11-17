package com.dazzle.asklepios.service.dto.externalresult;

import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExternalLabResultItemDTO(
        @NotBlank @Size(max = 255) String testName,
        LocalDate resultDate,
        @Size(max = 255) String resultValue,
        @Size(max = 50) String unit,
        @Size(max = 150) String normalRangeValue,
        TestResultMarker marker,
        @Size(max = 255) String physicianName
) {
}
