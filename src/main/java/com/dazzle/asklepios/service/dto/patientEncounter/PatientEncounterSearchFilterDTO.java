package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;

import java.time.LocalDate;
import java.util.List;

public record PatientEncounterSearchFilterDTO(
        Long departmentId,

        LocalDate fromDate,
        LocalDate toDate,

        List<EncounterStatus> statuses,

        String patientName,
        String mrn,

        List<EncounterReason> encounterReasons,

        String chiefComplaint,

        List<EncounterPriority> priorities
) {}