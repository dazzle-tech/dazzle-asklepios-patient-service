package com.dazzle.asklepios.domain.enumeration;

/** ACTIVE: items can still be ordered. COMPLETED: every quantity is used. CANCELLED: cancelled before any use. */
public enum PatientPackageStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}
