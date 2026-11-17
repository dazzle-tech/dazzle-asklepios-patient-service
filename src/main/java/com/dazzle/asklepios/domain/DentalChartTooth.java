package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DentalChartToothInventoryStatus;
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

@Entity
@Table(name = "dental_chart_tooth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalChartTooth extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    // Plain string, not an enum: holds either a ToothNumber name (permanent
    // dentition, e.g. "Tooth5") or a PrimaryToothNumber name (primary/child
    // dentition, e.g. "ToothC") depending on the owning chart's chartType.
    // Kept as a raw string rather than reusing ToothNumber so that enum
    // (shared with DentalProcedure) is never touched by pediatric support.
    @NotNull
    @Column(name = "tooth_number", nullable = false)
    private String toothNumber;

    @Column(name = "missing", nullable = false)
    private boolean missing = false;

    /** ACTIVE teeth form the chart inventory; REMOVED/REPLACED rows are kept for their clinical references. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_status", nullable = false, length = 20)
    @Builder.Default
    private DentalChartToothInventoryStatus inventoryStatus = DentalChartToothInventoryStatus.ACTIVE;

    /** Rows loaded before the column existed have no status and count as ACTIVE. */
    public boolean isActiveInventory() {
        return inventoryStatus == null || inventoryStatus == DentalChartToothInventoryStatus.ACTIVE;
    }
}
