package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaseelEncounterEmergency(
        String emergencyArrivalCode,
        String emergencyServiceStart,
        String emergencyDepartmentDisposition,
        String triageCategory,
        String triageDate
) {}
