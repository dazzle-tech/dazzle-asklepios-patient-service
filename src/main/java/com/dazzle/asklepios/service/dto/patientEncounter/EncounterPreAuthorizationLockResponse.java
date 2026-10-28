package com.dazzle.asklepios.service.dto.patientEncounter;

/**
 * Whether the visit cannot start yet because a default service waits on pre-authorization.
 */
public record EncounterPreAuthorizationLockResponse(
        boolean locked,
        String message
) {}
