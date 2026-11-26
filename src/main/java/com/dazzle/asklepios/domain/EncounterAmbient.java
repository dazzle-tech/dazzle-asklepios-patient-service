package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AmbientConsentMethod;
import com.dazzle.asklepios.domain.enumeration.AmbientConsentStatus;
import com.dazzle.asklepios.domain.enumeration.AmbientMicrophoneSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "encounter_ambient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterAmbient extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false, length = 20)
    private AmbientConsentStatus consentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_method", length = 20)
    private AmbientConsentMethod consentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "microphone_source", length = 20)
    private AmbientMicrophoneSource microphoneSource;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "recording_started_at")
    private Instant recordingStartedAt;

    @Column(name = "recording_stopped_at")
    private Instant recordingStoppedAt;

    @Column(name = "recording_duration_ms")
    private Long recordingDurationMs;

    @Column(name = "recording_content_type", length = 120)
    private String recordingContentType;

    @Column(name = "recording_file_name", length = 255)
    private String recordingFileName;

    @Column(name = "recording_byte_size")
    private Long recordingByteSize;

    @Column(name = "space_key", length = 500)
    private String spaceKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_json", columnDefinition = "jsonb")
    private String draftJson;

    @Column(name = "draft_created_at")
    private Instant draftCreatedAt;

    @Override
    public Long getId() {
        return id;
    }
}
