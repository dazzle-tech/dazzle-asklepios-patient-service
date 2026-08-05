package com.dazzle.asklepios.integration.waseel.client.dto;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CountryName;

public record CountryResponseVM(
        Long id,
        CountryName name,
        String code,
        Boolean isActive
) {
}