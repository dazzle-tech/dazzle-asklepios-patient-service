package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPreferredHealthProfessionalResponseVM(
        Long id,
        Long patientId,
        Long practitionerId,
        String networkAffiliation,
        String relatedWith
) implements Serializable {

    public static PatientPreferredHealthProfessionalResponseVM ofEntity(PatientPreferredHealthProfessional entity) {
        return new PatientPreferredHealthProfessionalResponseVM(
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getPractitionerId(),
                entity.getNetworkAffiliation(),
                entity.getRelatedWith()
        );
    }
}
