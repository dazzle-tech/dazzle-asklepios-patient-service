package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.List;

public record ConsultationSubmitRequestDTO(

        @NotNull(message = "consultationIds is required")
        List<Long> consultationIds,

        Long submittedBy

) implements Serializable {
}

