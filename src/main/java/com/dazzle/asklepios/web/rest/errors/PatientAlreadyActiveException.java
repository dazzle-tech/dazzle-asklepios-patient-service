package com.dazzle.asklepios.web.rest.errors;

public class PatientAlreadyActiveException extends RuntimeException {
    public PatientAlreadyActiveException() {
        super("PATIENT_ALREADY_ACTIVE");
    }
}
