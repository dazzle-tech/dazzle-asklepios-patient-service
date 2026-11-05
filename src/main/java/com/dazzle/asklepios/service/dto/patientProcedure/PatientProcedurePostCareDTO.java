package com.dazzle.asklepios.service.dto.patientProcedure;

import java.io.Serializable;
import java.math.BigDecimal;

public record PatientProcedurePostCareDTO(
        Long diagnosisIcdId,
        String diagnosisDescription,
        String patientCondition,
        BigDecimal bloodPressureSystolic,
        BigDecimal bloodPressureDiastolic,
        BigDecimal heartRate,
        BigDecimal temperature,
        BigDecimal oxygenSaturation,
        String painScore,
        String painDescription,
        String recoveryNotes,
        String additionalObservations,
        Boolean equipmentCountDone,
        String countStatus,
        Boolean nauseaVomiting,
        Boolean awakeAndOriented,
        Boolean toleratingOralFluids,
        Boolean ambulatingIndependently,
        Boolean voidedUrine,
        Boolean noActiveBleeding,
        Boolean painScore4,
        String activity,
        String respiration,
        String circulation,
        String consciousness,
        String oxygenSaturationLevel,
        BigDecimal aldreteScore
) implements Serializable {}
