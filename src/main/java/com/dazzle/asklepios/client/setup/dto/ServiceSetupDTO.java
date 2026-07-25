package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceSetupDTO(
        Long id,
        String name,
        String abbreviation,
        String code,
        String category,
        BigDecimal price,
        Currency currency,
        Boolean isActive,
        Long facilityId,
        Boolean appointable,
        Integer parallelCapacityValue,
        Integer defaultDurationMinutes,
        Integer defaultBufferBeforeMinutes,
        Integer defaultBufferAfterMinutes,
        Long billingRuleId
) implements Serializable {
}