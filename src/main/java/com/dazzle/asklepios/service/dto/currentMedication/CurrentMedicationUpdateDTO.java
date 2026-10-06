package com.dazzle.asklepios.service.dto.currentMedication;

import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import com.dazzle.asklepios.domain.enumeration.UOM;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentMedicationUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        Long activeIngredientId,

        BigDecimal dosage,

        UOM unit,

        MedFrequency frequency,

        Date startDate,

        @NotNull
        Boolean patientIsFree,

        String freeText

) implements Serializable {
}