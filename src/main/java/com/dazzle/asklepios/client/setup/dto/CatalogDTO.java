package com.dazzle.asklepios.client.setup.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogDTO(
        Long id,
        String name
) {

}
