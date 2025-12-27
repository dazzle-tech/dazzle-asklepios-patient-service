package com.dazzle.asklepios.web.rest.vm.pphp;

import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPreferredHealthProfessionalCreateVM(
        @NotNull Long practitionerId,
        @NotNull Long facilityId,
        String networkAffiliation,
        String relatedWith
) implements Serializable {

    public static PatientPreferredHealthProfessionalCreateVM ofEntity(
            PatientPreferredHealthProfessional entity
    ) {
        return new PatientPreferredHealthProfessionalCreateVM(
                entity.getPractitionerId(),
                entity.getFacilityId(),
                entity.getNetworkAffiliation(),
                entity.getRelatedWith()
        );
    }
}
