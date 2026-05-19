package com.dazzle.asklepios.integration.waseel.client.dto;

public record CountryDistrictResponseVM(
        Long id,
        String name,
        String code,
        Long countryId,
        Boolean isActive
) {
}