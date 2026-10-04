package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.EncounterCancellationReason;
import jakarta.validation.constraints.NotNull;

public record PatientEncounterCancelDTO(

        @NotNull(message = "Cancellation reason is required")
        EncounterCancellationReason reason,

        String otherReason

) {
}