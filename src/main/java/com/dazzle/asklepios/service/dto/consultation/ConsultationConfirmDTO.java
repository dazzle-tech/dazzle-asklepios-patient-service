package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConsultationConfirmDTO(

        @NotNull(message = "confirmedBy is required")
        Long confirmedBy

) implements Serializable {
}
