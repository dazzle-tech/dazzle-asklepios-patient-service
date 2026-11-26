package com.dazzle.asklepios.domain.enumeration;

public enum MedicationOrderStatus {
    WAITING_DOUBLE_CHECK,
    ADMINISTERED,
    CANCELLED,
    DISCARDED,
    NEW,
    SUBMITTED,
    //For medication-level only if its doses have mixed statuses, never stored on a dose
    PARTIALLY,
}
