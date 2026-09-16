package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.FLACCPainLevel;
import com.dazzle.asklepios.domain.enumeration.FLACCPainScaleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "patient_flacc_pain_scale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FLACCPainScale extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;


    @Column(name = "face", nullable = false, length = 100)
    private String face;

    @Column(name = "legs", nullable = false, length = 100)
    private String legs;

    @Column(name = "activity", nullable = false, length = 100)
    private String activity;

    @Column(name = "cry", nullable = false, length = 100)
    private String cry;

    @Column(name = "consolability", nullable = false, length = 100)
    private String consolability;

    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private FLACCPainScaleStatus status;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "pain_level", nullable = false, length = 50)
    private FLACCPainLevel painLevel;
}