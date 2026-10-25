package com.dazzle.asklepios.service.dto.patientDocuments;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDocumentCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long countryId,

        @NotNull
        DocumentType type,

        @NotEmpty
        String number

) implements Serializable {

    public static PatientDocumentCreateDTO ofEntity(PatientDocument doc) {
        return new PatientDocumentCreateDTO(
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getCountryId(),
                doc.getType(),
                doc.getNumber()
        );
    }
}
