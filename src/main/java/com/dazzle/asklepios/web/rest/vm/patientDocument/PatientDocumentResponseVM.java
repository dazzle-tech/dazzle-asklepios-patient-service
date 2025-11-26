package com.dazzle.asklepios.web.rest.vm.patientDocument;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentCategory;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDocumentResponseVM(
        Long id,
        Long patientId,
        Long countryId,
        DocumentCategory category,
        DocumentType type,
        String number
) implements Serializable {

    public static PatientDocumentResponseVM ofEntity(PatientDocument doc) {
        return new PatientDocumentResponseVM(
                doc.getId(),
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getCountryId(),
                doc.getCategory(),
                doc.getType(),
                doc.getNumber()
        );
    }
}
