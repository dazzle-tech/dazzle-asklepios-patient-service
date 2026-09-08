package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EncounterListVM(
        Long id,
        Long patientId,
        String patientFullName,
        String mrn,
        Integer age,
        String gender,
        String documentType,
        String documentNumber,
        String primaryMobileNumber,
        String encounterNumber,
        LocalDate encounterDate,
        String encounterTime,
        EncounterType encounterType,
        Long departmentId,
        String departmentName,
        Long practitionerId,
        String practitionerName,
        String defaultServiceName,
        BigDecimal amount,
        String paymentStatus,
        String coverageType,
        String paymentType,
        String insuranceName,
        Boolean triageStarted,
        LocalDateTime doctorStartDateTime,
        String encounterStatus,
        TreatmentStatus treatmentStatus,
        Boolean isObserved
) {
}