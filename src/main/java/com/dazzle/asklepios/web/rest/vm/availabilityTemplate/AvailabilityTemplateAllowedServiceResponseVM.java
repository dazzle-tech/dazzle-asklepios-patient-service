package com.dazzle.asklepios.web.rest.vm.availabilityTemplate;

import com.dazzle.asklepios.domain.AvailabilityTemplateAllowedService;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;

import java.io.Serializable;

public record AvailabilityTemplateAllowedServiceResponseVM(
        Long id,
        EncounterReason service
) implements Serializable {

    public static AvailabilityTemplateAllowedServiceResponseVM ofEntity(AvailabilityTemplateAllowedService entity) {
        return new AvailabilityTemplateAllowedServiceResponseVM(
                entity.getId(),
                entity.getService()
        );
    }
}