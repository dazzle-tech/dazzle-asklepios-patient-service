package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TpaResolutionDTO(
        String status,
        String tpaNphiesId,
        String tpaName
) {
    public static final String SELECTION_REQUIRED = "SELECTION_REQUIRED";
    public static final String NPHIES_ID_MISSING = "NPHIES_ID_MISSING";
}
