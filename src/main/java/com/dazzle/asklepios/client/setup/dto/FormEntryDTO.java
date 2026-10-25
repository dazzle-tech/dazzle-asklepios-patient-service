package com.dazzle.asklepios.client.setup.dto;

/**
 * Minimal FormEntry projection from setup-service for supportive-document checks.
 */
public record FormEntryDTO(
        Long id,
        Long encounterId,
        Long patientId,
        Long templateId
) {
}
