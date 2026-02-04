package com.dazzle.asklepios.service.dto.patientPreferredHealthProfessional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPreferredHealthProfessionalCreateDTO(
        @NotNull Long practitionerId,
        String networkAffiliation,
        String relatedWith
) implements Serializable {
}
