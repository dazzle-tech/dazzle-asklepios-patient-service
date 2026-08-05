package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaseelClaimEncounter(
        String status,
        String encounterClass,
        String serviceType,
        LocalDate startDate,
        LocalDate endDate,
        String serviceEventType,
        Long serviceProvider,
        LocalDate periodEnd,
        String causeOfDeath
) {}
