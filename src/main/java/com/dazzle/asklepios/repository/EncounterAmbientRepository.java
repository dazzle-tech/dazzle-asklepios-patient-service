package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterAmbient;
import com.dazzle.asklepios.domain.enumeration.AmbientConsentMethod;
import com.dazzle.asklepios.domain.enumeration.AmbientConsentStatus;
import com.dazzle.asklepios.domain.enumeration.AmbientMicrophoneSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EncounterAmbientRepository extends JpaRepository<EncounterAmbient, Long> {
    List<EncounterAmbientSummary> findByEncounter_IdOrderByCreatedDateDesc(Long encounterId);

    <T> Optional<T> findById(Long id, Class<T> type);

    interface IdRef {
        Long getId();
    }

    interface EncounterAmbientSummary {
        Long getId();

        IdRef getEncounter();

        IdRef getPatient();

        AmbientConsentStatus getConsentStatus();

        AmbientConsentMethod getConsentMethod();

        AmbientMicrophoneSource getMicrophoneSource();

        Instant getConsentAt();

        Instant getRecordingStartedAt();

        Instant getRecordingStoppedAt();

        Long getRecordingDurationMs();

        String getRecordingContentType();

        String getRecordingFileName();

        Long getRecordingByteSize();

        Instant getDraftCreatedAt();

        Instant getCreatedDate();

        Instant getLastModifiedDate();
    }

    interface EncounterAmbientDetail extends EncounterAmbientSummary {
        String getDraftJson();
    }
}
