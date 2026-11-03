package com.dazzle.asklepios.integration.waseel.dto.claim;

/**
 * Result of refreshing the registry's open claims from Waseel.
 *
 * @param checked open claims sent to Waseel's claim search
 * @param updated claims whose status or approved amount changed
 */
public record ClaimTrackingSyncResponse(
        int checked,
        int updated
) {
}
