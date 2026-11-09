package com.dazzle.asklepios.service.dto.patientProcedure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientProcedurePostCareDTO(
        Set<Long> diagnosisIcdIds,
        String patientCondition,
        BigDecimal bloodPressureSystolic,
        BigDecimal bloodPressureDiastolic,
        BigDecimal heartRate,
        BigDecimal temperature,
        BigDecimal oxygenSaturation,
        String painAssessmentType,
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
