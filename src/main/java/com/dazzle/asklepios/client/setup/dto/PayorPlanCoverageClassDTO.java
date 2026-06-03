package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayorPlanCoverageClassDTO(
        Long id,
        Long planId,
        String coverageClassType,
        String coverageClassValue,
        String coverageClassName,
        Boolean isActive
) {}