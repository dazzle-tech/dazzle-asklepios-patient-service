package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DentalChartAgeRuleDTO(
        Long id,
        String name,
        Integer ageFrom,
        Integer ageTo,
        String chartType,
        String numberingSystem,
        List<String> toothNumbers,
        Boolean isActive
) {
}
