package com.dazzle.asklepios.service.dto.patientProcedure;

import java.io.Serializable;
import java.time.Instant;

public record PatientProcedurePerformanceDTO(
        Instant actualStartTime,
        Instant actualEndTime,
        Boolean anesthesiaUsed,
        String anesthesiaType,
        Instant anesthesiaStartTime,
        Instant anesthesiaEndTime,
        Long anesthesiaAdministeredById,
        Boolean timeOut,
        String procedureOutcome,
        String observations,
        String complicationType,
        String complicationSeverity,
        String actionsTaken,
        String additionalNotes,
        String homeInstruction,
        String homeInstructionNotes
) implements Serializable {}
