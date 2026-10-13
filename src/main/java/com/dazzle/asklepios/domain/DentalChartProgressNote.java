package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "dental_chart_progress_note")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalChartProgressNote extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    @NotNull
    @Column(name = "note", columnDefinition = "TEXT", nullable = false)
    private String note;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
