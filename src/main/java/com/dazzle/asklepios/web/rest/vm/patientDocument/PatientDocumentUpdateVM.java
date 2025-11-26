package com.dazzle.asklepios.web.rest.vm.patientDocument;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentCategory;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDocumentUpdateVM(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long countryId,
        DocumentCategory category,
        @NotNull DocumentType type,
        @NotNull String number
) implements Serializable {

    public static PatientDocumentUpdateVM ofEntity(PatientDocument doc) {
        return new PatientDocumentUpdateVM(
                doc.getId(),
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getCountryId(),
                doc.getCategory(),
                doc.getType(),
                doc.getNumber()
        );
    }
}
