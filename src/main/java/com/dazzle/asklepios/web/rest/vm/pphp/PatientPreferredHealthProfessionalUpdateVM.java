package com.dazzle.asklepios.web.rest.vm.pphp;

import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPreferredHealthProfessionalUpdateVM(
        @NotNull Long id,
        Long practitionerId,
        Long facilityId,
        String networkAffiliation,
        String relatedWith
) implements Serializable {

    public static PatientPreferredHealthProfessionalUpdateVM ofEntity(
            PatientPreferredHealthProfessional entity
    ) {
        return new PatientPreferredHealthProfessionalUpdateVM(
                entity.getId(),
                entity.getPractitionerId(),
                entity.getFacilityId(),
                entity.getNetworkAffiliation(),
                entity.getRelatedWith()
        );
    }
}
