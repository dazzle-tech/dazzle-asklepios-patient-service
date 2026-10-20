package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * One UOM relation of a group: a quantity in {@code fromUnit} multiplied by
 * {@code relation} is the same quantity in {@code toUnit}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UomGroupRelationDTO(
        Long id,
        BigDecimal relation,
        Unit fromUnit,
        Unit toUnit
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Unit(
            Long id,
            String uom
    ) {}
}
