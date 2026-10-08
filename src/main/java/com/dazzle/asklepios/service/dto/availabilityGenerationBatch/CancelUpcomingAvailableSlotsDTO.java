package com.dazzle.asklepios.service.dto.availabilityGenerationBatch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelUpcomingAvailableSlotsDTO(
        @NotBlank @Size(max = 255) String cancelReason
) {
}
