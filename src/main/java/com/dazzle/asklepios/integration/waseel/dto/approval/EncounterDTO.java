package com.dazzle.asklepios.integration.waseel.dto.approval;

public record EncounterDTO(
        String status,
        String encounterClass,
        String serviceType,
        String startDate,
        String serviceEventType,
        Long serviceProvider,
        String periodEnd,
        String causeOfDeath
) {}