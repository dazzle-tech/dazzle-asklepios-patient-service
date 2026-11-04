package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Id and name of a setup record (e.g. allergen, medication category class).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SetupNameDTO(
        Long id,
        String name
) {}
