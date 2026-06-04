package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PayorPlanType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayorPlanDTO(
        Long id,
        Long payorId,
        String name,
        String planType,
        String networkId,
        String coverageType,
        String payerNphiesId,
        String waseelPlanId,
        Boolean isActive
) {}
