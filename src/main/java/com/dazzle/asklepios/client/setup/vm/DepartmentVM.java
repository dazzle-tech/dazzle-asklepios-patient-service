package com.dazzle.asklepios.client.setup.vm;

/**
 * View Model for reading a Facility via REST.
 */
public record DepartmentVM(
        Long id,
        Long facilityId,
        String name
) {}
