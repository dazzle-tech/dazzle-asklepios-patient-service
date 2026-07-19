package com.dazzle.asklepios.service.dto.currentMedication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentMedicationUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long activeIngredientId,

        String instructions,

        @NotNull
        @PastOrPresent
        Date startDate

) implements Serializable {}