package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcedureSetupDTO(
        Long id,
        String name,
        String code,
        String categoryType,
        Boolean isAppointable,
        String indications,
        String contraindications,
        String preparationInstructions,
        String recoveryNotes,
        Boolean isActive,
        Long facilityId,
        Currency currency,
        Long price
) {
}