package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record ToothActionCancelDTO(
        @NotBlank(message = "Cancellation reason is required.") String cancellationReason
) implements Serializable {}
