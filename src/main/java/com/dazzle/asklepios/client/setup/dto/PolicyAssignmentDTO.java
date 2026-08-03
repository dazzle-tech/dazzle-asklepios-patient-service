package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyAssignmentDTO(
        Long id,
        PolicyDefinitionDTO policy,
        Boolean isRequired,
        Boolean isActive
) {
}
