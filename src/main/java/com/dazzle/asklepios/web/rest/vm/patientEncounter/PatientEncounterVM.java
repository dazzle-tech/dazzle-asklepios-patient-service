package com.dazzle.asklepios.web.rest.vm.patientEncounter;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record PatientEncounterVM(

        Long id,
        String encounterNumber,
        Patient patient,
        Long facilityId,
        Long departmentId,
        Long practitionerId,

        Appointment appointment,

        EncounterType encounterType,
        EncounterReason encounterReason,
        EncounterPriority priorityLevel,
        TreatmentStatus status,

        String originType,
        String originName,
        String notes,

        Integer departmentDailySequenceNumber,
        LocalDate encounterDate,
        LocalTime encounterTime,
        Instant startedDate,
        String startedBy,

        String chiefComplaint,

        Boolean hasPrescription,
        Boolean hasOrder,
        Boolean isObserved,
        Instant createdAt,
        LocalDateTime dischargeAt,
        String historyOfPresentIllness

) {

    public static PatientEncounterVM ofEntity(PatientEncounter encounter, Boolean hasOrder, Boolean hasPrescription, Boolean hasObservation) {
        if (encounter == null) return null;

        return new PatientEncounterVM(
                encounter.getId(),
                encounter.getEncounterNumber(),
                encounter.getPatient(),

                encounter.getFacilityId(),
                encounter.getDepartmentId(),
                encounter.getPractitionerId(),

                encounter.getAppointment()!=null? encounter.getAppointment() : null,

                encounter.getEncounterType(),
                encounter.getEncounterReason(),
                encounter.getPriorityLevel(),
                encounter.getStatus(),

                encounter.getOriginType(),
                encounter.getOriginName(),
                encounter.getNotes(),

                encounter.getDepartmentDailySequenceNumber(),
                encounter.getEncounterDate(),
                encounter.getEncounterTime(),
                encounter.getStartedDate(),
                encounter.getStartedBy(),
                encounter.getChiefComplaint(),
                hasPrescription,
                hasOrder,
                hasObservation,
                encounter.getCreatedDate(),
                encounter.getDischargeAt(),
                encounter.getHistoryOfPresentIllness()

        );
    }
}