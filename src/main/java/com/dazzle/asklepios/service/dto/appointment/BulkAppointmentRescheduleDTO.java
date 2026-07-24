package com.dazzle.asklepios.service.dto.appointment;

import jakarta.validation.constraints.NotNull;

public record BulkAppointmentRescheduleDTO(
   @NotNull Long originalAvailabilityGenerationBatchId,
   @NotNull Long replacementAvailabilityGenerationBatchId
) {
}
