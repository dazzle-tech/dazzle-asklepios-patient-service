package com.dazzle.asklepios.service.dto.patientProcedure;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

public record PatientProcedureMedicationDTO(
        @NotNull Long activeIngredientId,
        BigDecimal dose,
        String unit
) implements Serializable {}
