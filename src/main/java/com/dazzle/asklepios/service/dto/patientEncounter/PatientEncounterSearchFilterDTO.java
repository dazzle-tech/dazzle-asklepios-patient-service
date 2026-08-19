package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;

import java.time.LocalDate;
import java.util.List;

public record PatientEncounterSearchFilterDTO(
        Long departmentId,
        Long practitionerId,

        LocalDate fromDate,
        LocalDate toDate,

        List<TreatmentStatus> statuses,

        String patientName,
        String mrn,
        String encounterNumber,


        List<EncounterReason> encounterReasons,

        String chiefComplaint,

        List<EncounterPriority> priorities
) {}