package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "chief_complain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChiefComplain extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "chief_complaint", columnDefinition = "text", nullable = false)
    private String chiefComplaint;

    @Column(name = "provocation", length = 200)
    private String provocation;

    @Column(name = "palliation", length = 200)
    private String palliation;

    @Column(name = "quality", length = 50)
    private String quality;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "severity", length = 50, nullable = false)
    private String severity;

    @Column(name = "onset_date_time", nullable = false)
    private Instant onsetDateTime;

    @Column(name = "case_understanding", length = 500, nullable = false)
    private String caseUnderstanding;

    @Column(name = "patient_condition", length = 50)
    @Enumerated(EnumType.STRING)
    private PatientCondition patientCondition;

    @Column(name = "is_triage", nullable = false)
    private Boolean isTriage = false;
}