package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ToothNumber;
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
@Table(name = "tooth_action")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToothAction extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "tooth_id", nullable = false)
    private Long toothId;

    @NotNull
    @Column(name = "dental_action_id", nullable = false)
    private Long dentalActionId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "surface", nullable = false)
    private ToothSurface surface;

    @Column(name = "existing", nullable = false)
    private boolean existing = false;

    @Column(name = "image_name")
    private String imageName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tooth_number", nullable = false)
    private ToothNumber toothNumber;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;
}
