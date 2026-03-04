package com.dazzle.asklepios.service.dto.encounterVaccination;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public record EncounterVaccinationCancelDTO(

        @NotNull
        Long id,

        @NotBlank
        String cancellationReason

) implements Serializable {}
