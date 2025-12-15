package com.dazzle.asklepios.web.rest.vm.pphp;

import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPreferredHealthProfessionalResponseVM(
        Long id,
        Long patientId,
        Long practitionerId,
        Long facilityId,
        String networkAffiliation,
        String relatedWith
) implements Serializable {

    public static PatientPreferredHealthProfessionalResponseVM ofEntity(
            PatientPreferredHealthProfessional entity
    ) {
        return new PatientPreferredHealthProfessionalResponseVM(
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getPractitionerId(),
                entity.getFacilityId(),
                entity.getNetworkAffiliation(),
                entity.getRelatedWith()
        );
    }
}
