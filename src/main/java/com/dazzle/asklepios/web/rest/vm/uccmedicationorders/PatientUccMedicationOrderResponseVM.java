package com.dazzle.asklepios.web.rest.vm.uccmedicationorders;

import com.dazzle.asklepios.domain.UrgentCareMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.domain.enumeration.Unit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record PatientUccMedicationOrderResponseVM(
        Long id,
        Long activeIngredientId,
        MedicationInstructionType instructionType,
        String instructionText,
        Long dose,
        String doseUnit,
        String route,
        Integer frequencyNumber,
        Unit frequencyUnit,
        Integer duration,
        LocalTime startTime,
        LocalDateTime doseTime,
        MedicationOrderStatus status,
        Instant administeredDate,
        String administeredBy,
        Instant doubleCheckedDate,
        String doubleCheckedBy,
        Instant discardedDate,
        String discardedBy,
        String discardReason,
        Instant cancelledDate,
        String cancelledBy,
        String cancellationReason,
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static PatientUccMedicationOrderResponseVM ofEntity(UrgentCareMedicationOrder entity) {
        return new PatientUccMedicationOrderResponseVM(
                entity.getId(),
                entity.getActiveIngredientId(),
                entity.getInstructionType(),
                entity.getInstructionText(),
                entity.getDose(),
                entity.getDoseUnit(),
                entity.getRoute(),
                entity.getFrequencyNumber(),
                entity.getFrequencyUnit(),
                entity.getDuration(),
                entity.getStartTime(),
                entity.getDoseTime(),
                entity.getStatus(),
                entity.getAdministeredDate(),
                entity.getAdministeredBy(),
                entity.getDoubleCheckedDate(),
                entity.getDoubleCheckedBy(),
                entity.getDiscardedDate(),
                entity.getDiscardedBy(),
                entity.getDiscardReason(),
                entity.getCancelledDate(),
                entity.getCancelledBy(),
                entity.getCancellationReason(),
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}