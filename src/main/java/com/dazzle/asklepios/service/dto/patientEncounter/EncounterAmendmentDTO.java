package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.TypeOfReopen;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterAmendmentDTO(
        @NotNull
        TypeOfReopen typeOfReopen,
        @NotBlank
        @Size(max = 2000)
        String reason
) implements Serializable {
}
