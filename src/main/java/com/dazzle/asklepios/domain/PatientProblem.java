package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "patient_problems")
@EqualsAndHashCode(callSuper = false)
public class PatientProblem extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotBlank
    @Column(name = "condition", nullable = false)
    private String condition;

    @NotNull
    @Column(name = "date_of_diagnosis", nullable = false)
    private Date dateOfDiagnosis;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_status", nullable = false)
    private EncounterVaccinationStatus conditionStatus;

    @NotNull
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "date_of_resolution")
    private Date dateOfResolution;

    @NotNull
    @Column(name = "by_patient", nullable = false)
    private Boolean byPatient;

    @Column(name = "source_of_information")
    private String sourceOfInformation;


    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PatientHistoryStatus status = PatientHistoryStatus.ACTIVE;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Date cancelledDate;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
}