package com.dazzle.asklepios.domain.enumeration.waseelIntegration;

import java.util.Locale;

/**
 * Payer decision on a claim as Waseel's claim search reports it. Each value carries the claim status it leads to;
 * a null claim status means the decision does not change the claim (still waiting, or not a decision).
 */
public enum WaseelClaimStatus {
    PENDED(null),
    QUEUED(null),
    APPROVED(ClaimStatus.ACCEPTED),
    PARTIALLY_APPROVED(ClaimStatus.ACCEPTED),
    REJECTED(ClaimStatus.REJECTED),
    CANCELLED(null),
    ERROR(null),
    UNKNOWN(null);

    private final ClaimStatus claimStatus;

    WaseelClaimStatus(ClaimStatus claimStatus) {
        this.claimStatus = claimStatus;
    }

    /** The claim status this decision leads to, or null when the claim stays as it is. */
    public ClaimStatus claimStatus() {
        return claimStatus;
    }

    /**
     * Reads Waseel's status text ("pended", "partially approved", ...). A value not listed here is UNKNOWN.
     * Returns null when Waseel sent no status.
     */
    public static WaseelClaimStatus fromWaseel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (normalized.contains("partial")) {
            return PARTIALLY_APPROVED;
        }
        if (normalized.contains("reject") || normalized.contains("denied") || normalized.startsWith("not")) {
            return REJECTED;
        }
        if (normalized.contains("approve") || normalized.contains("accept")) {
            return APPROVED;
        }
        if (normalized.contains("cancel")) {
            return CANCELLED;
        }
        if (normalized.contains("error") || normalized.contains("fail")) {
            return ERROR;
        }
        return switch (normalized) {
            case "pended", "pending" -> PENDED;
            case "queued" -> QUEUED;
            default -> UNKNOWN;
        };
    }
}
