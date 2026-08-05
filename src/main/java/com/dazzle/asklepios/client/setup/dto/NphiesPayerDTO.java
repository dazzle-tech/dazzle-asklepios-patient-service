package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NphiesPayerDTO(
        Long id,
        String nphiesId,
        String nameEn,
        String nameAr,
        Boolean isActive
) {}
