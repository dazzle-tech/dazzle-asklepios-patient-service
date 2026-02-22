package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientWarningStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "patient_warnings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientWarnings extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    @NonNull
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    @NonNull
    private Long encounterId;

    @Column(name = "warning_type", nullable = false)
    @NotBlank
    private String warningType;

    @Column(name = "warning")
    @NotBlank
    private String warning;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    @NonNull
    private Severity severity;

    @Column(name = "onset_date_undefined", nullable = false)
    private boolean onsetDateUndefined = true;

    @Column(name = "onset_date")
    private Instant onsetDate;

    @Column(name = "by_patient")
    private boolean byPatient = true;

    @Column(name = "source_of_information")
    private String sourceOfInformation;

    @Column(name = "note")
    private String note;

    @Column(name = "action_taken")
    private String actionTaken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PatientWarningStatus status;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_date")
    private Instant resolvedDate;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancellation_reason")
    private String cancellationReason;


}