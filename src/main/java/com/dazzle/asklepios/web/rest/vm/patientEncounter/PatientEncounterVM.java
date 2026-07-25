package com.dazzle.asklepios.web.rest.vm.patientEncounter;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterLifecycleStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PatientEncounterVM(

        Long id,
        String encounterNumber,
        Patient patient,
        Long facilityId,
        Long departmentId,
        Long practitionerId,

        AppointmentFromTemplate appointment,

        EncounterType encounterType,
        EncounterReason encounterReason,
        EncounterPriority priorityLevel,
        EncounterLifecycleStatus encounterStatus,
        TreatmentStatus treatmentStatus,

        String originType,
        String originName,
        String notes,

        Integer departmentDailySequenceNumber,
        LocalDate encounterDate,

        Instant startedDate,
        String startedBy,

        String chiefComplaint,

        Boolean hasPrescription,
        Boolean hasOrder,
        Boolean isObserved,
        Instant createdAt,
        LocalDateTime dischargeAt

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
                encounter.getEncounterStatus(),
                encounter.getTreatmentStatus(),

                encounter.getOriginType(),
                encounter.getOriginName(),
                encounter.getNotes(),

                encounter.getDepartmentDailySequenceNumber(),
                encounter.getEncounterDate(),
                encounter.getStartedDate(),
                encounter.getStartedBy(),
                encounter.getChiefComplaint(),
                hasPrescription,
                hasOrder,
                hasObservation,
                encounter.getCreatedDate(),
                encounter.getDischargeAt()

        );
    }
}
