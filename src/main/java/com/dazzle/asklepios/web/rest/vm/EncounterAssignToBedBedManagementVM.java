package com.dazzle.asklepios.web.rest.vm;

public record EncounterAssignToBedBedManagementVM(
        Long bedId,
        Long patientId,
        String patientName
) {
}