package com.dazzle.asklepios.service.dto.encounterVaccination;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public record EncounterVaccinationReviewDTO(

        @NotNull
        Long id

) implements Serializable {}
