package com.dazzle.asklepios.web.rest.vm.CurrentMedication;

import com.dazzle.asklepios.domain.CurrentMedication;

import java.time.Instant;
import java.util.Date;

public record CurrentMedicationResponseVM(
        Long id,
        Long patientId,
        Long activeIngredientId,
        String instructions,
        Date startDate,

        // Cancel fields
        String status,
        String cancelledBy,
        Date cancelledDate,
        String cancellationReason,

        // Audit fields
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static CurrentMedicationResponseVM ofEntity(CurrentMedication entity) {
        return new CurrentMedicationResponseVM(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getActiveIngredientId(),
                entity.getInstructions(),
                entity.getStartDate(),

                // Cancel fields
                entity.getStatus(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason(),

                // Audit fields
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}