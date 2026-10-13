package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ToothActionSaveDTO(
        Long id,
        @NotNull Long toothId,
        @NotNull Long dentalActionId,
        String note,
        @NotNull ToothSurface surface,
        Long cdtCodeId,
        boolean existing
) implements Serializable {}
