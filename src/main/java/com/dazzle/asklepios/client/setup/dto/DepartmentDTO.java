package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepartmentDTO(
        Long id,
        Long facilityId,
        String name,
        Boolean appointable,
        EncounterType encounterType,
        Boolean isActive
) {

}
