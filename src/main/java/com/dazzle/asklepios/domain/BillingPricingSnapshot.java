package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.CalculationOrder;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.ExemptionType;
import com.dazzle.asklepios.domain.enumeration.billing.PricingReason;
import com.dazzle.asklepios.domain.enumeration.billing.PricingSource;
import com.dazzle.asklepios.domain.enumeration.billing.RoundingModeType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;
import com.fasterxml.jackson.databind.JsonNode;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table( name = "billing_pricing_snapshot")
public class BillingPricingSnapshot extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_line_id", nullable = false)
    private BillingChargeLine chargeLine;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_service_product_id", nullable = false)
    private PatientServiceAndProduct patientServiceProduct;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @Column(name = "price_list_id")
    private Long priceListId;

    @Column(name = "price_list_item_id")
    private Long priceListItemId;

    @Column(name = "billing_configuration_id")
    private Long billingConfigurationId;

    @Column(name = "discount_id")
    private Long discountId;

    @Column(name = "tax_id")
    private Long taxId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_source", nullable = false, length = 50)
    private PricingSource pricingSource;

    @Column(name = "pricing_version")
    private Long pricingVersion;

    @Size(max = 100)
    @Column(name = "price_list_code", length = 100)
    private String priceListCode;

    @Size(max = 255)
    @Column(name = "price_list_name", length = 255)
    private String priceListName;

    @Size(max = 100)
    @Column(name = "price_list_item_code", length = 100)
    private String priceListItemCode;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "base_unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseUnitPrice;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 30)
    private DiscountType discountType;

    @NotNull
    @DecimalMin(value = "0.000000")
    @DecimalMax(value = "100.000000")
    @Column(name = "discount_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Size(max = 500)
    @Column(name = "discount_reason", length = 500)
    private String discountReason;

    @Size(max = 50)
    @Column(name = "discount_approved_by", length = 50)
    private String discountApprovedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "exemption_type", length = 30)
    private ExemptionType exemptionType;

    @NotNull
    @DecimalMin(value = "0.000000")
    @DecimalMax(value = "100.000000")
    @Column(name = "exemption_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal exemptionRate = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "exemption_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal exemptionAmount = BigDecimal.ZERO;

    @Size(max = 500)
    @Column(name = "exemption_reason", length = 500)
    private String exemptionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", length = 30)
    private TaxType taxType;

    @NotNull
    @DecimalMin(value = "0.000000")
    @DecimalMax(value = "100.000000")
    @Column(name = "tax_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "taxable_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_order", nullable = false, length = 50)
    private CalculationOrder calculationOrder;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false, length = 30)
    private RoundingModeType roundingMode;

    @NotNull
    @Min(0)
    @Column(name = "rounding_scale", nullable = false)
    private Integer roundingScale = 4;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "patient_responsibility_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal patientResponsibilityAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "insurance_responsibility_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal insuranceResponsibilityAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "other_payer_responsibility_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherPayerResponsibilityAmount = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_payload", columnDefinition = "jsonb")
    private JsonNode calculationPayload;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingPricingSnapshotStatus status = BillingPricingSnapshotStatus.ACTIVE;

    @NotNull
    @Column(name = "effective_date", nullable = false)
    private Instant effectiveDate;

    @Column(name = "superseded_date")
    private Instant supersededDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_snapshot_id")
    private BillingPricingSnapshot supersededBySnapshot;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_reason", nullable = false, length = 100)
    private PricingReason pricingReason;

    @Size(max = 500)
    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            name = "price_source",
            nullable = false,
            length = 30
    )
    private BillingPriceSource priceSource;

    @Column(name = "setup_source_id")
    private Long setupSourceId;
}