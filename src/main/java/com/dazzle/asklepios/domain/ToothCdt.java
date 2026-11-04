package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "tooth_cdt")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToothCdt extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "tooth_id", nullable = false)
    private Long toothId;

    @NotNull
    @Column(name = "cdt_code_id", nullable = false)
    private Long cdtCodeId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ServiceSource source;

    @NotNull
    @Column(name = "tooth_action_id", nullable = false)
    private Long toothActionId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "surface", nullable = false)
    private ToothSurface surface;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;
}
