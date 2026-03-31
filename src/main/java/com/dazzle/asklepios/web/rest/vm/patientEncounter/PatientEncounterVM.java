package com.dazzle.asklepios.web.rest.vm.patientEncounter;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;

import java.time.LocalDate;

public record PatientEncounterVM(

        Long id,
        String encounterNumber,
        Patient patient,
        Long facilityId,
        Long departmentId,
        Long practitionerId,

        String appointmentId,

        EncounterType encounterType,
        EncounterReason encounterReason,
        EncounterPriority priorityLevel,
        EncounterStatus status,

        String originType,
        String originName,
        String notes,

        Integer departmentDailySequenceNumber,
        LocalDate encounterDate,

        String chiefComplaint,

        Boolean hasPrescription,
        Boolean hasOrder,
        Boolean isObserved

) {

    public static PatientEncounterVM ofEntity(PatientEncounter encounter,Boolean hasOrder,Boolean hasPrescription,Boolean hasObservation) {
        if (encounter == null) return null;

        return new PatientEncounterVM(
                encounter.getId(),
                encounter.getEncounterNumber(),
                encounter.getPatient(),

                encounter.getFacilityId(),
                encounter.getDepartmentId(),
                encounter.getPractitionerId(),

                encounter.getAppointmentId(),

                encounter.getEncounterType(),
                encounter.getEncounterReason(),
                encounter.getPriorityLevel(),
                encounter.getStatus(),

                encounter.getOriginType(),
                encounter.getOriginName(),
                encounter.getNotes(),

                encounter.getDepartmentDailySequenceNumber(),
                encounter.getEncounterDate(),

                encounter.getChiefComplaint(),
                hasPrescription,
                hasOrder,
                hasObservation

        );
    }
}