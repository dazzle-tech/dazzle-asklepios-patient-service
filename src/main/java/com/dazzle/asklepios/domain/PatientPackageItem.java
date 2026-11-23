package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/** One item of a bought package with how much of it is used. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Table(name = "patient_package_item")
public class PatientPackageItem extends AbstractAuditingEntity<Long> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "patient_package_id", nullable = false)
    private Long patientPackageId;

    @Column(name = "package_setup_item_id")
    private Long packageSetupItemId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private BillingItemTypes itemType;

    @NotNull
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "item_code", length = 255)
    private String itemCode;

    @Column(name = "item_name", length = 1000)
    private String itemName;

    @NotNull
    @Column(name = "quantity_total", nullable = false)
    private Integer quantityTotal;

    @NotNull
    @Column(name = "quantity_used", nullable = false)
    @Builder.Default
    private Integer quantityUsed = 0;

    public int getQuantityRemaining() {
        int total = quantityTotal == null ? 0 : quantityTotal;
        int used = quantityUsed == null ? 0 : quantityUsed;
        return Math.max(0, total - used);
    }
}
