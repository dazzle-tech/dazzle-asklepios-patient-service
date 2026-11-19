package com.dazzle.asklepios.integration.nahdi.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Send status of a prescription at Nahdi, shown on the prescription screen.
 *
 * @param hasUnsentChanges   a medication was added, edited or cancelled after the last successful send
 *                           (or the prescription was never accepted by Nahdi)
 * @param lastSentAt         last successful send, if any
 * @param lastAttemptStatus  status of the last attempt (SUCCESS / FAILED), if any
 * @param lastAttemptAt      time of the last attempt, if any
 * @param orderNumber        Nahdi order number of the last successful send
 * @param failureReason      readable reason when the last attempt failed
 * @param integrationEnabled false when the Nahdi integration is off (nothing is sent)
 */
@Builder(builderClassName = "Builder")
public record NahdiSyncStatus(
        boolean hasUnsentChanges,
        Instant lastSentAt,
        String lastAttemptStatus,
        Instant lastAttemptAt,
        String orderNumber,
        String failureReason,
        boolean integrationEnabled
) {}
