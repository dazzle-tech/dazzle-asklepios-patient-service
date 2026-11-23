package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
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

/** One use of a package item: the visit, the quantity and the order it made. Filled from phase 3. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Table(name = "patient_package_usage")
public class PatientPackageUsage extends AbstractAuditingEntity<Long> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "patient_package_id", nullable = false)
    private Long patientPackageId;

    @NotNull
    @Column(name = "patient_package_item_id", nullable = false)
    private Long patientPackageItemId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Same values as patient_services_and_products.service_source. */
    @Column(name = "order_source", length = 50)
    private String orderSource;

    @Column(name = "order_id")
    private Long orderId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PatientPackageUsageStatus status = PatientPackageUsageStatus.USED;
}
