package com.dazzle.asklepios.service.dto.currentMedication;

import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import com.dazzle.asklepios.domain.enumeration.UOM;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentMedicationCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long activeIngredientId,

        BigDecimal dosage,

        UOM unit,

        MedFrequency frequency,

        @NotNull
        @PastOrPresent
        Date startDate

) implements Serializable {}