package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/** One saved inventory batch: one reason, applied atomically with its audit rows. */
public record ChartInventoryUpdateDTO(
        @NotNull Long expectedVersion,
        @NotNull Long encounterId,
        @NotBlank @Size(max = 500) String reason,
        @NotEmpty @Valid List<ChartInventoryChangeDTO> changes
) implements Serializable {}
