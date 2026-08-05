package com.dazzle.asklepios.integration.waseel.event;

import java.util.List;

/**
 * Schedules deferred billing for items after pre-authorization approval commits.
 */
public record PreAuthorizationApprovedEvent(
        Long encounterId,
        List<Long> patientServiceProductIds
) {}
