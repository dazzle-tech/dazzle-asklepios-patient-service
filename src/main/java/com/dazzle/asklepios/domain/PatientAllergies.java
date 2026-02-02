package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AllergenTypes;
import com.dazzle.asklepios.domain.enumeration.PatientAllergyStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patient_allergies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientAllergies extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allergen_type", nullable = false)
    private AllergenTypes allergenType;

    @Column(name = "allergen_id")
    private Long allergenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "medication_class_id")
    private Long medicationClassId;

    @Column(name = "criticality")
    private String criticality;

    @Column(name = "certainty")
    private String certainty;

    @Column(name = "treatment_strategy")
    private String treatmentStrategy;

    @Column(name = "onset")
    private String onset;

    @Column(name = "onset_date_undefined")
    private boolean onsetDateUndefined = true;

    @Column(name = "onset_date")
    private Instant onsetDate;

    @Column(name = "type_of_propensity")
    private String typeOfPropensity;

    @Column(name = "by_patient")
    private boolean byPatient = true;

    @Column(name = "source_of_information")
    private String sourceOfInformation;

    @Column(name = "note")
    private String note;

    @Column(name = "allergic_reactions")
    private String allergicReactions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PatientAllergyStatus status;

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
