package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.List;

public record ConsultationSubmitRequestDTO(

        @NotEmpty(message = "consultationIds is required")
        List<Long> consultationIds

) implements Serializable {
}
