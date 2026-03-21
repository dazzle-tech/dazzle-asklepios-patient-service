package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples;

import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosticOrderTestSampleLabelDTO(
        Long orderTestId,
        String patientName,
        String facilityName,
        String mrn,
        String testName,
        Instant sampleDateTime,
        BigDecimal sampleQuantity,
        String sampleUnit
) {}