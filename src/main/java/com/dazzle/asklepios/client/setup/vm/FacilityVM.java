package com.dazzle.asklepios.client.setup.vm;

/**
 * View Model for reading a Facility via REST.
 */
public record FacilityVM(
        Long id,
        String name,
        String code
) {}
