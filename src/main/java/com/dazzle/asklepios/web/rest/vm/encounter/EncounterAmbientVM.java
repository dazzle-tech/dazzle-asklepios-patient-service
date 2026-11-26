package com.dazzle.asklepios.web.rest.vm.encounter;

import com.dazzle.asklepios.domain.enumeration.AmbientConsentMethod;
import com.dazzle.asklepios.domain.enumeration.AmbientConsentStatus;
import com.dazzle.asklepios.domain.enumeration.AmbientMicrophoneSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record EncounterAmbientVM(
    Long id,
    Long encounterId,
    Long patientId,
    AmbientConsentStatus consentStatus,
    AmbientConsentMethod consentMethod,
    AmbientMicrophoneSource microphoneSource,
    Instant consentAt,
    Instant recordingStartedAt,
    Instant recordingStoppedAt,
    Long recordingDurationMs,
    String recordingContentType,
    String recordingFileName,
    Long recordingByteSize,
    boolean hasRecording,
    JsonNode draft,
    Instant draftCreatedAt,
    Instant createdDate,
    Instant lastModifiedDate
) {}
