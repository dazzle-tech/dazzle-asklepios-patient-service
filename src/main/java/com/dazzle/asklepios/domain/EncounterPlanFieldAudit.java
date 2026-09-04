package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "patient_encounter_plan_field_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterPlanFieldAudit implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_plan_id", nullable = false)
    private Long encounterPlanId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "log_date", nullable = false)
    private Instant logDate;

    @Column(name = "log_by", nullable = false)
    private String logBy;
}