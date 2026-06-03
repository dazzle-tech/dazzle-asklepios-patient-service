package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.CountryName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CountryDTO(
        Long id,
        CountryName name,
        String code,
        Boolean isActive
) implements Serializable {
}
