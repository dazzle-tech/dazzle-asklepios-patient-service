package com.dazzle.asklepios.service.dto.patientDocuments;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDocumentUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long countryId,

        @NotNull
        DocumentType type,

        @NotNull
        String number,

        @NotNull
        Boolean isPrimary

) implements Serializable {

    public static PatientDocumentUpdateDTO ofEntity(PatientDocument doc) {
        return new PatientDocumentUpdateDTO(
                doc.getId(),
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getCountryId(),
                doc.getType(),
                doc.getNumber(),
                doc.getIsPrimary()
        );
    }
}
