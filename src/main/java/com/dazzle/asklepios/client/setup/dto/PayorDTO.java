package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayorDTO(
        Long id,
        String code,
        String name,

        LocalDate startDate,
        LocalDate expiryDate,
        Boolean isActive
) {

}
