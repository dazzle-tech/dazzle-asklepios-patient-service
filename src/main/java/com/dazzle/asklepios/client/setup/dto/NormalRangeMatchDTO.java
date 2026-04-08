package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.AgeUnit;
import com.dazzle.asklepios.domain.enumeration.Condition;
import com.dazzle.asklepios.domain.enumeration.NormalRangeType;

import java.util.List;

/**
 * DTO returned from setup-service to represent a normal range row.
 * Used by patient-service to pick the best matching range for a patient.
 */
public record NormalRangeMatchDTO(
        Long id,
        Long profileTestId,

        // optional reference only
        Long testId,

        // matching criteria (nullable means "not specified" => general)
        String gender,
        Double ageFrom,
        AgeUnit ageFromUnit,
        Double ageTo,
        AgeUnit ageToUnit,
        Condition condition,

        // range definition
        String resultText,
        String resultLov,
        NormalRangeType normalRangeType,
        Double rangeFrom,
        Double rangeTo,

        // critical thresholds
        Boolean criticalValue,
        Double criticalValueLessThan,
        Double criticalValueMoreThan,

        // LOV options (only when profile resultType == LOV)
        List<String> lovKeys
) {}
