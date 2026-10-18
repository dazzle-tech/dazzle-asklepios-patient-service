package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One saved Patient Dashboard configuration for a user on a single encounter.
 * A missing row means the encounter was never customized. A row with no widgets
 * means the user explicitly saved an empty selection.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "encounter_dashboard_preference")
public class EncounterDashboardPreference extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Builder.Default
    @OneToMany(mappedBy = "preference", fetch = FetchType.LAZY)
    private List<EncounterDashboardPreferenceWidget> widgets = new ArrayList<>();
}
