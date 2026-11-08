package com.dazzle.asklepios.service.dto.patientProcedure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientProcedurePreAssessmentDTO(
        Boolean fastingRequired,
        Boolean patientPrepared,
        String specialInstructions,
        BigDecimal bloodPressureSystolic,
        BigDecimal bloodPressureDiastolic,
        BigDecimal heartRate,
        BigDecimal temperature,
        BigDecimal oxygenSaturation,
        Boolean patientIdentityVerified,
        Boolean consentConfirmed,
        Boolean procedureSiteMarked,
        Boolean allergiesConfirmed,
        Boolean patientPremedicated,
        Boolean equipmentCountingDone,
        String asaScore,
        String airwayGrade
) implements Serializable {}
