package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.patientarrived;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record PatientArrivedCreateRequestDTO
        (
        @NotNull Instant patientArrivedDate,
        String patientArrivedNoteRad
) implements Serializable {}