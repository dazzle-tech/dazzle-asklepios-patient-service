package com.dazzle.asklepios.domain.enumeration;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;

/** Where one tooth of a dental procedure stands with the insurance, read from its billing line. */
public enum DentalToothApprovalStatus {
    NO_APPROVAL_NEEDED,
    PENDING_APPROVAL,
    APPROVED,
    PARTIALLY_APPROVED,
    REJECTED,
    CANCELLED;

    public static DentalToothApprovalStatus of(PatientServiceAndProduct line) {
        if (line.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return CANCELLED;
        }
        PreAuthorizationStatus status = line.getPreAuthorizationStatus();
        if (status == null) {
            return NO_APPROVAL_NEEDED;
        }
        return switch (status) {
            case PENDING_APPROVAL -> PENDING_APPROVAL;
            case APPROVED -> APPROVED;
            case PARTIAL -> PARTIALLY_APPROVED;
            case REJECTED -> REJECTED;
            // A pre-authorization cancelled on a line still in use: the line waits for a new one.
            case CANCELLED -> Boolean.TRUE.equals(line.getPreAuthorizationRequired()) ? PENDING_APPROVAL : NO_APPROVAL_NEEDED;
            case NOT_REQUIRED -> NO_APPROVAL_NEEDED;
        };
    }
}
