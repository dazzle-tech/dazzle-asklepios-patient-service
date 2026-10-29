package com.dazzle.asklepios.domain.enumeration;

/**
 * What a history entry of a patient activity records.
 */
public enum PatientActivityAction {
    CREATED,
    UPDATED,
    ASSIGNED,
    DUE_CHANGED,
    PRIORITY_CHANGED,
    CHECKLIST_CHECKED,
    CHECKLIST_UNCHECKED,
    COMMENTED,
    COMPLETED,
    CANCELLED
}
