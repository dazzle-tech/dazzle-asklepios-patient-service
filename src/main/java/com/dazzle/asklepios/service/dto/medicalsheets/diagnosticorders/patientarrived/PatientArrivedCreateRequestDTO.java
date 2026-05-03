package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.patientarrived;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.time.Instant;

public record PatientArrivedCreateRequestDTO
        (
                @PastOrPresent(message = "patient arrivel  at cannot be in the future")
                @NotNull Instant patientArrivedDate,
        String patientArrivedNoteRad
) implements Serializable {}