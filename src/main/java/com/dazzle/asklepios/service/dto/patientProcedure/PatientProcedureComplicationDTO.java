package com.dazzle.asklepios.service.dto.patientProcedure;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public record PatientProcedureComplicationDTO(
        @NotBlank String complicationType,
        String severity,
        String actionsTaken
) implements Serializable {}
