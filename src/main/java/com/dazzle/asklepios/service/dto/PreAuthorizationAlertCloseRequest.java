package com.dazzle.asklepios.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PreAuthorizationAlertCloseRequest(
        @NotNull Long encounterId,
        @NotBlank String message,
        @Valid List<Item> items
) {
    public record Item(
            Long patientServiceProductId,
            String label
    ) {
    }
}
