package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
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
import jakarta.validation.constraints.FutureOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "patient_prescription_medications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientPrescriptionMedication extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_header_id", nullable = false)
    private PatientPrescription prescriptionHeader;

    @Column(name = "medications_id", nullable = false)
    private Long medicationsId;

    @Enumerated(EnumType.STRING)
    @Column(name = "instructions_type", nullable = false, length = 50)
    private PrescriptionInstructionsType instructionsType;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "dose")
    private Long dose;

    @Column(name = "does_unit", length = 100)
    private String doesUnit;

    @Column(name = "rout", length = 100)
    private String rout;

    @Column(name = "frequency", length = 100)
    private String frequency;

    @Column(name = "duration")
    private Long duration;

    @Column(name = "duration_type", length = 50)
    private String durationType;

    @Column(name = "chronic_medication", nullable = false)
    private Boolean chronicMedication = false;

    @Column(name = "maximum_dose")
    private Long maximumDose;

    @FutureOrPresent
    @Column(name = "valid_util")
    private LocalDate validUtil;

    @Column(name = "allowed_substitute")
    private Boolean allowedSubstitute;

    @Column(name = "indication_manually", columnDefinition = "text")
    private String indicationManually;

    @Column(name = "indication_use", columnDefinition = "text")
    private String indicationUse;

    @Column(name = "indication_icd", nullable = false)
    private Long indicationIcd;

    @Column(name = "administration_instructions")
    private String administrationInstructions;

    @Column(name = "parameters_to_monitor", columnDefinition = "text")
    private String parametersToMonitor;

    @Column(name = "number_of_refills")
    private Long numberOfRefills;

    @Column(name = "refill_value")
    private Integer refillValue;

    @Column(name = "refill_unit", length = 50)
    private String refillUnit;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "extra_documentation", columnDefinition = "text")
    private String extraDocumentation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private PrescriptionStatus status;

}