package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PayorPlanType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayorPlanDTO(
        Long id,
        Long payorId,
        String name,
        String waseelPlanId,
        PayorPlanType coverageType,
        String networkId,
        String policyClassName
) {
}
