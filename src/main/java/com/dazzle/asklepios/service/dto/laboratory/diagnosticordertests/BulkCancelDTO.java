package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkCancelDTO(
        @NotEmpty List<Long> ids,
        @NotBlank String cancellationReason
) {
}
