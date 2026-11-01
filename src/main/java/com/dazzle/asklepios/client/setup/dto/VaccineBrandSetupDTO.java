package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VaccineBrandSetupDTO(
        Long id,
        String name,
        String code,
        Long facilityId,
        BigDecimal price,
        String currency,
        Boolean isActive
) {}
