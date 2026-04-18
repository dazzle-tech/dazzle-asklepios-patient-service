package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosticTestSetupDTO(
        Long id,
        TestType type,
        String name,
        String internalCode,
        BigDecimal price,
        Currency currency,
        Boolean isActive
) {
}