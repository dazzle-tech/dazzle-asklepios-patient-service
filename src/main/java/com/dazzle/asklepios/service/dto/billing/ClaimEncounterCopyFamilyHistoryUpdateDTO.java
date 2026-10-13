package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Relations;
import jakarta.validation.constraints.NotNull;

public record ClaimEncounterCopyFamilyHistoryUpdateDTO(
        String condition,
        Relations relation,
        Boolean inheritedDiseases,
        @NotNull Boolean patientIsFree,
        String freeText
) {
}