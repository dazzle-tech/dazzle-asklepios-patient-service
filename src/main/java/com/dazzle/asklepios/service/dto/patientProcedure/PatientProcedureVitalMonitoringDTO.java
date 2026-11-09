package com.dazzle.asklepios.service.dto.patientProcedure;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record PatientProcedureVitalMonitoringDTO(
        @NotNull Instant recordedAt,
        BigDecimal heartRate,
        BigDecimal oxygenSaturation,
        BigDecimal bloodPressureSystolic,
        BigDecimal bloodPressureDiastolic,
        BigDecimal respiratoryRate,
        BigDecimal temperature,
        BigDecimal etco2,
        BigDecimal fluidsGiven,
        BigDecimal urineOutput
) implements Serializable {}
