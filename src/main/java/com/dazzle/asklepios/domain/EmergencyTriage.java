package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AVPUScale;
import com.dazzle.asklepios.domain.enumeration.EmergencyLevel;
import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.dazzle.asklepios.domain.enumeration.TriageDestination;
import com.dazzle.asklepios.domain.enumeration.YesNoQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "emergency_triage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyTriage extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "emergency_level", length = 50)
    @Enumerated(EnumType.STRING)
    private EmergencyLevel emergencyLevel;

    @Column(name = "right_eye_light_response")
    private Boolean rightEyeLightResponse = false;

    @Column(name = "right_eye_pupil_size", length = 50)
    private String rightEyePupilSize;

    @Column(name = "left_eye_light_response")
    private Boolean leftEyeLightResponse = false;

    @Column(name = "left_eye_pupil_size", length = 50)
    private String leftEyePupilSize;

    @Column(name = "hpi_additional_notes", columnDefinition = "text")
    private String hpiAdditionalNotes;

    @Column(name = "life_saving", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion lifeSaving;

    @Column(name = "unresponsive", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion unresponsive;

    @Column(name = "high_risk", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion highRisk;

    @Column(name = "avpu_scale", length = 50)
    @Enumerated(EnumType.STRING)
    private AVPUScale avpuScale;

    @Column(name = "pain_score", length = 50)
    @Enumerated(EnumType.STRING)
    private PainLevel painScore;

    @Column(name = "labs_required", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion labsRequired;

    @Column(name = "imaging_required", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion imagingRequired;

    @Column(name = "iv_fluids_required", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion ivFluidsRequired;

    @Column(name = "medication_required", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion medicationRequired;

    @Column(name = "ecg_required", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion ecgRequired;

    @Column(name = "consultation_required", length = 50)
    @Enumerated(EnumType.STRING)
    private YesNoQuestion consultationRequired;

    @Column(name = "destination", length = 50)
    @Enumerated(EnumType.STRING)
    private TriageDestination destination;

    @Column(name = "completed_date")
    private Instant completedDate ;
}