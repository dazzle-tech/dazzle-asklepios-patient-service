package com.dazzle.asklepios.integration.nahdi.dto;

import java.time.Instant;

/**
 * Whether a submitted prescription has changes that were not sent to Nahdi yet.
 *
 * @param hasUnsentChanges  a medication was added, edited or cancelled after the last successful send
 *                          (or the prescription was never accepted by Nahdi)
 * @param lastSentAt        last successful send, if any
 * @param lastAttemptStatus status of the last attempt (SUCCESS / FAILED), if any
 */
public record NahdiSyncStatus(
        boolean hasUnsentChanges,
        Instant lastSentAt,
        String lastAttemptStatus
) {}
