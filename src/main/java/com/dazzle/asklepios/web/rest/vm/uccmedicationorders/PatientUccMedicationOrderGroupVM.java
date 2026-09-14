package com.dazzle.asklepios.web.rest.vm.uccmedicationorders;

import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.domain.enumeration.Unit;

import java.time.LocalTime;

public record PatientUccMedicationOrderGroupVM(
        Long orderGroupId,
        Long patientId,
        Long encounterId,
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
        Integer doseCount,
        MedicationOrderStatus status,
        Boolean isHighAlert
) {
}