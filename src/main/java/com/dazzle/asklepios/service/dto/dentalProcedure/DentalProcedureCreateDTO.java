package com.dazzle.asklepios.service.dto.dentalProcedure;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.ToothNumber;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record DentalProcedureCreateDTO(
        @NotNull Patient patientId,
        @NotNull PatientEncounter encounterId,
        @NotNull ToothNumber toothNumber,
        @NotNull String surface,
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        @NotNull Long serviceId,
        Long cdtCodeId,
        String notes
) implements Serializable {}