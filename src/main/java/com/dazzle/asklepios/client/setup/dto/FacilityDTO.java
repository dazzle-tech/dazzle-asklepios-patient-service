package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FacilityDTO(
        Long id,
        String name,
        Long defaultLabDepartmentId,
        String defaultLabDepartmentName,
        Long defaultRadDepartmentId,
        String defaultRadDepartmentName
) {

}
