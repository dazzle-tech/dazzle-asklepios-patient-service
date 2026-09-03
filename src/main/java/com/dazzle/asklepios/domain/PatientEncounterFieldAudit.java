package com.dazzle.asklepios.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "patient_encounter_field_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientEncounterFieldAudit implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_encounter_id", nullable = false)
    private PatientEncounter patientEncounter;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "operation_type", nullable = false, length = 10)
    private String operationType;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "log_date", nullable = false)
    private Instant logDate;

    @Column(name = "log_by", length = 50)
    private String logBy;
}