package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
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

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;

@Entity
@Table(name = "claim_encounter_copy_patient_problems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimEncounterCopyPatientProblem
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_encounter_copy_id", nullable = false)
    private Long claimEncounterCopyId;

    @Column(name = "patient_problem_id")
    private Long patientProblemId;

    @Column(name = "condition")
    private String condition;

    @Column(name = "date_of_diagnosis")
    private Date dateOfDiagnosis;

    @Column(name = "condition_status")
    private String conditionStatus;

    @Column(name = "type")
    private String type;

    @Column(name = "date_of_resolution")
    private Date dateOfResolution;

    @Column(name = "by_patient")
    private Boolean byPatient;

    @Column(name = "source_of_information")
    private String sourceOfInformation;

    @Column(name = "patient_is_free", nullable = false)
    @Builder.Default
    private Boolean patientIsFree = Boolean.FALSE;

    @Column(name = "free_text", columnDefinition = "text")
    private String freeText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PatientHistoryStatus status = PatientHistoryStatus.ACTIVE;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;
}
