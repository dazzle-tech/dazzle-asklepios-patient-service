package com.dazzle.asklepios.web.rest.vm.patientPortal;

public record PatientPortalLoginVM(
        Long patientId,
        String medicalRecordNumber,
        String firstName,
        String lastName,
        String token
) {
}
