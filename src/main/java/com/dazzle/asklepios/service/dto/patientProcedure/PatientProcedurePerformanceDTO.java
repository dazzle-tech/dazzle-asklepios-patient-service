package com.dazzle.asklepios.service.dto.patientProcedure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        String additionalNotes,
        String homeInstruction,
        String homeInstructionNotes
) implements Serializable {}
