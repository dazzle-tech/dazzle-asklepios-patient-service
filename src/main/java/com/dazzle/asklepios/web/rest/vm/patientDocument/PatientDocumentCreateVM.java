package com.dazzle.asklepios.web.rest.vm.patientDocument;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serializable;
@JsonIgnoreProperties(ignoreUnknown = true)

public record PatientDocumentCreateVM(
        @NotNull Long patientId,
        @NotNull Long countryId,
        @NotNull DocumentType type,
        @NotEmpty String number,
        Boolean isPrimary
) implements Serializable {

    public static PatientDocumentCreateVM ofEntity(PatientDocument doc) {
        return new PatientDocumentCreateVM(
                doc.getPatient() != null ? doc.getPatient().getId() : null,
                doc.getCountryId(),
                doc.getType(),
                doc.getNumber(),
                doc.getIsPrimary()
        );
    }
}
