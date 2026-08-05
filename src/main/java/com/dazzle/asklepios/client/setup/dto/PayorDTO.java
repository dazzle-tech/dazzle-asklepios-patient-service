package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayorDTO(
        Long id,
        String code,
        String name,

        String nphiesId,
        String waseelPayerId,
        String tpaNphiesId,
        Boolean isWaseelEnabled,

        LocalDate startDate,
        LocalDate expiryDate,
        Boolean isActive
) {}