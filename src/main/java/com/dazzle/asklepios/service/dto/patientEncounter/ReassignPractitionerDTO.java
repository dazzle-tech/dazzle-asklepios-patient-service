package com.dazzle.asklepios.service.dto.patientEncounter;

import jakarta.validation.constraints.NotNull;

public record ReassignPractitionerDTO(
        @NotNull
        Long practitionerId
) {
}
