package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EncounterListFilterDTO(

        Long facilityId,

        LocalDate fromDate,
        LocalDate toDate,

        String patientName,
        String mrn,

        Integer ageFrom,
        Integer ageTo,

        String gender,

        String documentType,
        String documentNumber,
        String primaryMobileNumber,

        EncounterType encounterType,
        String encounterNumber,

        Long departmentId,
        Long practitionerId,

        String defaultServiceName,

        java.math.BigDecimal amountFrom,
        java.math.BigDecimal amountTo,

        String paymentStatus,
        String coverageType,
        String paymentType,
        String insuranceName,

        Boolean triageStarted,

        LocalDateTime doctorStartedFrom,
        LocalDateTime doctorStartedTo,

        List<String> encounterStatusIn,
        List<TreatmentStatus> treatmentStatusIn,

        List<EncounterReason> encounterReasons

) {
}