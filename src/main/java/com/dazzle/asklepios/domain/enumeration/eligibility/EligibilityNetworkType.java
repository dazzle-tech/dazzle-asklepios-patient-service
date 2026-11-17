package com.dazzle.asklepios.domain.enumeration.eligibility;

import java.util.Locale;

/** Network of a benefit row ("In Network" / "Out of Network"). */
public enum EligibilityNetworkType {
    IN_NETWORK,
    OUT_OF_NETWORK;

    /** {@code null} when the payer does not say. */
    public static EligibilityNetworkType fromText(String network) {
        String text = network == null ? "" : network.trim().toLowerCase(Locale.ROOT);
        if (text.contains("out of") || text.contains("out-of") || text.startsWith("out")) {
            return OUT_OF_NETWORK;
        }
        if (text.contains("in network") || text.contains("in-network") || text.equals("in")) {
            return IN_NETWORK;
        }
        return null;
    }
}
