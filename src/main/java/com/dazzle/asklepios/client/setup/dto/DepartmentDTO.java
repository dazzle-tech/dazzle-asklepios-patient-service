package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepartmentDTO(
        Long id,
        Long facilityId,
        String name,
        Boolean appointable,
        EncounterType encounterType,
        Boolean isActive,
        Integer defaultDurationMinutes,
        List<WorkingDayJson> workingDays

) {

}
