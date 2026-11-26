package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * One inventory change. {@code action} is ADD, REMOVE or REPLACE. For REPLACE,
 * {@code toothNumber} is the tooth being replaced and {@code replacementToothNumber} its successor.
 */
public record ChartInventoryChangeDTO(
        @NotBlank String action,
        @NotBlank String toothNumber,
        String replacementToothNumber
) implements Serializable {}
