package com.dazzle.asklepios.service.dto.dentalHistory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record DentalHistoryUpdateDTO(
        @NotNull Long id,
        @NotBlank String historyText
) implements Serializable {}
