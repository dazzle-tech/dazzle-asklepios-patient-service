package com.dazzle.asklepios.service.dto.patientPortal;

public record PatientPortalLoginDTO(
        String primaryDocumentNumber,
        String pin
) {
}
