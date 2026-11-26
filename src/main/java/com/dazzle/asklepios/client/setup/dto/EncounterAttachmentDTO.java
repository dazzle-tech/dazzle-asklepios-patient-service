package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * An attachment of a visit as the setup service stores it. The file itself is in the attachments
 * bucket under spaceKey; source is the screen it was uploaded from, sourceId the record there.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterAttachmentDTO(
        Long id,
        Long encounterId,
        String spaceKey,
        String filename,
        String mimeType,
        Long sizeBytes,
        String type,
        String source,
        Long sourceId,
        Instant createdDate
) {
}
