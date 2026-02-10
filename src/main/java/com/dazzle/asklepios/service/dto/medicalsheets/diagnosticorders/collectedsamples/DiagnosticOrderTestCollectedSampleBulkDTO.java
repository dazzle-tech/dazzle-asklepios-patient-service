package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DiagnosticOrderTestCollectedSampleBulkDTO(
        @NotNull Long orderId,
        @NotEmpty @Valid List<CollectedSampleItemDTO> items
) {}
