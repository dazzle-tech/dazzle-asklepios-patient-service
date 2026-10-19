package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Predefined prescription instruction from setup; unit, route and frequency are enum names (e.g. "EVERY_8_HOURS").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescriptionInstructionDTO(
        Long id,
        BigDecimal dose,
        String unit,
        String rout,
        String frequency
) {}
