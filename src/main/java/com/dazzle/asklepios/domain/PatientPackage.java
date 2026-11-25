package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
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
import java.math.BigDecimal;

/** A package bought for a patient. Code, name and price are copied from the setup when it is bought. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Table(name = "patient_package")
public class PatientPackage extends AbstractAuditingEntity<Long> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** The visit where the package was chosen. */
    @Column(name = "encounter_id")
    private Long encounterId;

    @NotNull
    @Column(name = "package_setup_id", nullable = false)
    private Long packageSetupId;

    @Column(name = "package_code", length = 50)
    private String packageCode;

    @Column(name = "package_name", length = 255)
    private String packageName;

    /** Empty when the package has no fixed price (its items carry discounts instead). */
    @Column(name = "package_price", precision = 19, scale = 4)
    private BigDecimal packagePrice;

    /** Copied from the setup: the visit's default service is left out (and cancelled if already made). */
    @Column(name = "override_default_service", nullable = false)
    @Builder.Default
    private Boolean overrideDefaultService = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PatientPackageStatus status = PatientPackageStatus.ACTIVE;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;
}
