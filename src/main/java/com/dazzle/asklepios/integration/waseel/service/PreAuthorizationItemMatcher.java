package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Matches a pre-authorization request line to the billing line it was sent for. */
public final class PreAuthorizationItemMatcher {

    private PreAuthorizationItemMatcher() {
    }

    /**
     * Same service, procedure, test or medication, and the same tooth when both lines have one:
     * a dental procedure on several teeth sends one line per tooth with the same procedure.
     */
    public static boolean matches(PreAuthorizationItem requestLine, PatientServiceAndProduct billingLine) {
        if (requestLine == null || billingLine == null) {
            return false;
        }
        boolean sameItem = sameId(requestLine.getServiceId(), billingLine.getServiceId())
                || sameId(requestLine.getProcedureId(), billingLine.getProcedureId())
                || sameId(requestLine.getDiagnosticTestId(), billingLine.getDiagnosticTestId())
                || sameId(requestLine.getBrandMedicationId(), billingLine.getBrandMedicationId());
        return sameItem && sameToothWhenKnown(requestLine.getBodySite(), billingLine.getToothNumber());
    }

    /** The request line of the billing line; a request with a single line owns it whatever its ids. */
    public static Optional<PreAuthorizationItem> find(List<PreAuthorizationItem> requestLines, PatientServiceAndProduct billingLine) {
        if (requestLines == null || requestLines.isEmpty()) {
            return Optional.empty();
        }
        Optional<PreAuthorizationItem> matched = requestLines.stream()
                .filter(requestLine -> matches(requestLine, billingLine))
                .findFirst();
        return matched.isPresent() || requestLines.size() > 1 ? matched : Optional.of(requestLines.get(0));
    }

    private static boolean sameId(Long requestId, Long billingId) {
        return requestId != null && Objects.equals(requestId, billingId);
    }

    private static boolean sameToothWhenKnown(String bodySite, String toothNumber) {
        return bodySite == null || bodySite.isBlank() || toothNumber == null || bodySite.trim().equals(toothNumber.trim());
    }
}
