package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PractitionerDTO(
        Long id,
        List<WorkingDayJson> workingDays,
        String firstName,
        String lastName

) {

}
