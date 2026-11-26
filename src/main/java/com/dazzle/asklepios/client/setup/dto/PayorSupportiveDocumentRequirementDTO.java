package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayorSupportiveDocumentRequirementDTO(
        Long payorId,
        Boolean configured,
        List<String> requirementCodes
) {
}
