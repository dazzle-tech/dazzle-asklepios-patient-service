package com.dazzle.asklepios.service.dto.patientDocuments;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientNoDocumentCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        DocumentType type,

        Boolean isPrimary

) implements Serializable {

    public static PatientNoDocumentCreateDTO ofEntity(PatientDocument doc) {
        return new PatientNoDocumentCreateDTO(
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getType(),
                doc.getIsPrimary()
        );
    }
}
