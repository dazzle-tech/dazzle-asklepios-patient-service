package com.dazzle.asklepios.domain.enumeration;


public enum TreatmentStatus {
    NEW,
    ONGOING,
    CANCELLED,
    CLOSED,
    COMPLETED,DISCHARGED,
    IN_OPERATION,
    CONFIRM_RETURN,
    TEMP_DC,
    TRIAGE_STARTED,
    SENT_TO_ER,
    WAITING_TRIAGE,
    WAITING_LIST,
    PENDING_PAYMENT,
    ASSIGNED_TO_BED;

    public static TreatmentStatus fromDatabaseValue(String value) {
        if (value == null) {
            return null;
        }
        if ("OPEN".equals(value)) {
            return ONGOING;
        }
        return valueOf(value);
    }}
