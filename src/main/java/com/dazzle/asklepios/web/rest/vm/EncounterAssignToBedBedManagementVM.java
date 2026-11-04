package com.dazzle.asklepios.web.rest.vm;

/**
 * Lightweight active-assignment projection for bed boards / bed management.
 * Includes encounterId so clients can enrich clinical context without N+1 lookups.
 */
public record EncounterAssignToBedBedManagementVM(
        Long id,
        Long bedId,
        Long encounterId,
        Long patientId,
        String patientName
) {
}
