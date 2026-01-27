package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AgeGroupType;
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
@Table(name = "additional_measurements")
public class AdditionalMeasurements extends AbstractAuditingEntity<Long> implements Serializable {

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
    @Column(name = "age_group", nullable = false, length = 20)
    private AgeGroupType ageGroup;

    @Column(name = "hearing_test", columnDefinition = "text")
    private String hearingTest;

    @Builder.Default
    @Column(name = "dehydration", nullable = false)
    private Boolean dehydration = false;

    @Builder.Default
    @Column(name = "nasal_flaring", nullable = false)
    private Boolean nasalFlaring = false;

    @Builder.Default
    @Column(name = "response_to_light", nullable = false)
    private Boolean responseToLight = false;

    @Builder.Default
    @Column(name = "pupil_response", nullable = false)
    private Boolean pupilResponse = false;

    @Builder.Default
    @Column(name = "ability_to_follow_target", nullable = false)
    private Boolean abilityToFollowTarget = false;

    @Builder.Default
    @Column(name = "color_testing", nullable = false)
    private Boolean colorTesting = false;

    @Builder.Default
    @Column(name = "fall_risk", nullable = false)
    private Boolean fallRisk = false;

    @Builder.Default
    @Column(name = "vision_problems_affecting_function", nullable = false)
    private Boolean visionProblemsAffectingFunction = false;

    @Builder.Default
    @Column(name = "hearing_problems_affecting_function", nullable = false)
    private Boolean hearingProblemsAffectingFunction = false;

    @Column(name = "details", columnDefinition = "text")
    private String details;

    @Column(name = "action_to_take", columnDefinition = "text")
    private String actionToTake;

    @NotNull
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
