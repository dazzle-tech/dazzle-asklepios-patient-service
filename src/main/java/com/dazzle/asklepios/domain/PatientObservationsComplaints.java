package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BloodGroup;
import com.dazzle.asklepios.domain.enumeration.ModeOfArrival;
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "patient_observations_complaints")
public class PatientObservationsComplaints extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "functional_status", columnDefinition = "text")
    private String functionalStatus;

    @Column(name = "patient_conditions", columnDefinition = "text")
    private String patientConditions;

    @NotNull
    @Column(name = "reason_of_visit", columnDefinition = "text")
    private String reasonOfVisit;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_of_arrival", length = 30)
    private ModeOfArrival modeOfArrival;

    @Column(name = "by_patient", nullable = false)
    private Boolean byPatient = true;

    @Column(name = "source_of_information")
    private Long sourceOfInformation;

    @Column(name = "cognitive_check", columnDefinition = "text")
    private String cognitiveCheck;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 20)
    private BloodGroup bloodGroup;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}