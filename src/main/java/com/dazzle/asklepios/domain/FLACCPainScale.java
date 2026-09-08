package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.FLACCPainLevel;
import com.dazzle.asklepios.domain.enumeration.FLACCPainScaleStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@Entity
@Table(name = "patient_flacc_pain_scale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FLACCPainScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;


    @Column(name = "face_lov", nullable = false, length = 100)
    private String faceLov;

    @Column(name = "legs_lov", nullable = false, length = 100)
    private String legsLov;

    @Column(name = "activity_lov", nullable = false, length = 100)
    private String activityLov;

    @Column(name = "cry_lov", nullable = false, length = 100)
    private String cryLov;

    @Column(name = "consolability_lov", nullable = false, length = 100)
    private String consolabilityLov;

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

    @CreatedBy
    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 50)
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private Instant lastModifiedDate;

    @Transient
    @JsonProperty("painLevel")
    private FLACCPainLevel painLevel;
}