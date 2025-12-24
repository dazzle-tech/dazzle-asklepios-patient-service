package com.dazzle.asklepios.web.rest.vm.patientDocument;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientNoDocumentCreateVM(
        @NotNull Long patientId,
        @NotNull DocumentType type,
        Boolean isPrimary
) implements Serializable {

    public static PatientNoDocumentCreateVM ofEntity(PatientDocument doc) {
        return new PatientNoDocumentCreateVM(
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getType(),
                doc.getIsPrimary()
        );
    }
}
