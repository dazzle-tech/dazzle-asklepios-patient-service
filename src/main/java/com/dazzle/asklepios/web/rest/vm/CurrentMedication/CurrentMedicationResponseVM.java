package com.dazzle.asklepios.web.rest.vm.CurrentMedication;

import com.dazzle.asklepios.domain.CurrentMedication;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;

import java.time.Instant;
import java.util.Date;

public record CurrentMedicationResponseVM(
        Long id,
        Long patientId,
        Long activeIngredientId,
        String instructions,
        Date startDate,

        PatientHistoryStatus status,
        String cancelledBy,
        Date cancelledDate,
        String cancellationReason,

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

                entity.getStatus(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason(),

                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}