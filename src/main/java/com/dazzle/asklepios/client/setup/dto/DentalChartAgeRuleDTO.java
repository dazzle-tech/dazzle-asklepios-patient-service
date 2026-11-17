package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DentalChartAgeRuleDTO(
        Long id,
        String name,
        Integer ageFrom,
        Integer ageTo,
        String chartType,
        String numberingSystem,
        Long numberingMethodId,
        Map<String, String> numberingLabels,
        List<String> toothNumbers,
        Boolean isActive
) {
}
