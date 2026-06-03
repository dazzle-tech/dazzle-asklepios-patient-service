package com.dazzle.asklepios.integration.waseel.dto.approval;

import java.time.LocalDate;

public record WaseelApprovalEncounter(
        String status,
        String encounterClass,
        String serviceType,
        LocalDate startDate,
        String serviceEventType,
        String serviceProvider,
        LocalDate periodEnd,
        String causeOfDeath
) {}