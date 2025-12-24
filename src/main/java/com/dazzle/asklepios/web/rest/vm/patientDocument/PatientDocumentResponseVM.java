package com.dazzle.asklepios.web.rest.vm.patientDocument;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDocumentResponseVM(
        Long id,
        Long patientId,
        Long countryId,
        DocumentType type,
        String number,
        Boolean isPrimary,
        java.time.Instant createdDate,
        String createdBy,
        java.time.Instant lastModifiedDate,
        String lastModifiedBy
) implements Serializable {

    public static PatientDocumentResponseVM ofEntity(PatientDocument doc) {
        return new PatientDocumentResponseVM(
                doc.getId(),
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getCountryId(),
                doc.getType(),
                doc.getNumber(),
                doc.getIsPrimary(),
                doc.getCreatedDate(),
                doc.getCreatedBy(),
                doc.getLastModifiedDate(),
                doc.getLastModifiedBy()
        );
    }
}
