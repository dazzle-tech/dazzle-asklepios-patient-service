package com.dazzle.asklepios.integration.nahdi.dto.dispensing;

import java.time.Instant;
import java.util.List;

/**
 * Dispensing status of a prescription as reported by Nahdi.
 *
 * @param available false when the status could not be obtained (source not configured yet, or the call failed);
 *                  the stored status is then kept as it is
 * @param lines     one entry per prescription medication Nahdi reported on
 * @param message   reason when not available
 */
public record NahdiDispensingStatus(
        boolean available,
        List<Line> lines,
        String message
) {

    /**
     * @param medicationId prescription medication id (our line)
     * @param dispensed    true when dispensed and not returned
     * @param dispensedAt  when it was dispensed, if known
     * @param reference    pharmacy reference, e.g. invoice number
     */
    public record Line(
            Long medicationId,
            boolean dispensed,
            Instant dispensedAt,
            String reference
    ) {}

    public static NahdiDispensingStatus unavailable(String message) {
        return new NahdiDispensingStatus(false, List.of(), message);
    }

    public static NahdiDispensingStatus of(List<Line> lines) {
        return new NahdiDispensingStatus(true, lines, null);
    }
}
