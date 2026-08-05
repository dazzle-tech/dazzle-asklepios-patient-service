package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityClassDTO(
        String classType,
        String className,
        String classValue
) {}