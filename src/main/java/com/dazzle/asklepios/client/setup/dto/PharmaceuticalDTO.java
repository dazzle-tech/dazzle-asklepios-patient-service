package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PharmaceuticalDTO(
        Long id,
        Long activeIngredientId,
        String activeIngredientName,
        String code,
        String billingCode,
        String itemDescription,
        String secondDescription,
        Boolean isActive,
        String pharmaceuticalName
) {}
