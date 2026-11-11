package com.dazzle.asklepios.integration.nahdi.dto.dispensing;

import java.time.Instant;
import java.util.List;

/**
 * Dispensing state of a prescription after a refresh.
 *
 * @param refreshed              true when the status was obtained from Nahdi now; false when the stored status is shown
 * @param message                reason when not refreshed
 * @param checkedAt              time of this check
 * @param dispensedMedicationIds medications currently marked dispensed
 */
public record NahdiDispensingSummary(
        boolean refreshed,
        String message,
        Instant checkedAt,
        List<Long> dispensedMedicationIds
) {}
