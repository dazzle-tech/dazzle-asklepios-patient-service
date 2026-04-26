package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.dazzle.asklepios.domain.enumeration.Severity;
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
@Table(name = "pain_assessment")
public class PainAssessment extends AbstractAuditingEntity<Long> implements Serializable {

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pain_degree", length = 200)
    private Severity painDegree;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pain_level")
    private PainLevel painLevel;

    @Column(name = "pain_pattern", length = 255)
    private String painPattern;

    @Column(name = "pain_description", columnDefinition = "text")
    private String painDescription;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
