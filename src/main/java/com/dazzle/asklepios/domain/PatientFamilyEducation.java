package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientFamilyEducationMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "patient_family_education")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "patient", "encounter" })
public class PatientFamilyEducation extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Getter(AccessLevel.NONE)
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Getter(AccessLevel.NONE)
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @NotBlank
    @Column(name = "topic", nullable = false, columnDefinition = "TEXT")
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 50)
    private PatientFamilyEducationMethod method;

    @Column(name = "effectiveness", columnDefinition = "TEXT")
    private String effectiveness;

    @JsonIgnore
    public Patient getPatient() {
        return patient;
    }

    @JsonIgnore
    public PatientEncounter getEncounter() {
        return encounter;
    }

    public Long getPatientId() {
        return patient == null ? null : patient.getId();
    }

    public Long getEncounterId() {
        return encounter == null ? null : encounter.getId();
    }
}
