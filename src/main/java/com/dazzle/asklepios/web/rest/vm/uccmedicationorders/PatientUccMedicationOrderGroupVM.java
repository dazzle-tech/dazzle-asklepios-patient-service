package com.dazzle.asklepios.web.rest.vm.uccmedicationorders;

import com.dazzle.asklepios.domain.enumeration.FrequencyList;
import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.domain.enumeration.Unit;

import java.time.Instant;
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
        FrequencyList frequencyUnit,
        Integer duration,
        LocalTime startTime,
        Integer doseCount,
        MedicationOrderStatus status,
        Boolean isHighAlert,
        MedFrequency frequency,
        // Who cancelled this medication, if the doctor cancelled it
        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason,
        // True while some dose is still NEW or SUBMITTED, so the doctor can cancel
        Boolean hasOpenDoses
) {
}