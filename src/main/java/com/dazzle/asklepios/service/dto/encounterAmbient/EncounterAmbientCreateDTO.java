package com.dazzle.asklepios.service.dto.encounterAmbient;

import com.dazzle.asklepios.domain.enumeration.AmbientConsentMethod;
import com.dazzle.asklepios.domain.enumeration.AmbientConsentStatus;
import com.dazzle.asklepios.domain.enumeration.AmbientMicrophoneSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterAmbientCreateDTO(
    @NotNull Long patientId,
    @NotNull Long encounterId,
    @NotNull AmbientConsentStatus consentStatus,
    AmbientConsentMethod consentMethod,
    AmbientMicrophoneSource microphoneSource,
    Instant consentAt,
    Instant recordingStartedAt,
    Instant recordingStoppedAt,
    Long recordingDurationMs,
    JsonNode draft
) implements Serializable {
}
