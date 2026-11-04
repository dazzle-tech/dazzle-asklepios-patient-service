package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ToothServiceSaveDTO(
        @NotNull Long toothId,
        @NotNull Long serviceId
) implements Serializable {}
