package com.dazzle.asklepios.client.setup.vm;

/**
 * View Model for reading a Facility via REST.
 */
public record PractitionerVM(
        Long id,
        Long facilityId,
        String firstName,
        String lastName
) {}
