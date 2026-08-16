package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.PriceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
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
import java.math.BigDecimal;

@Entity
@Table(name = "patient_services_and_products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientServiceAndProduct extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_item_type", nullable = false, length = 50)
    private BillingItemTypes billingItemType;

    @Column(name = "brand_medication_id")
    private Long brandMedicationId;

    @Column(name = "diagnostic_test_id")
    private Long diagnosticTestId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "procedure_id")
    private Long procedureId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "service_source", nullable = false, length = 50)
    private ServiceSource serviceSource;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Long quantity = 1L;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "exemption_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal exemptionAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private Currency currency;

    @Column(name = "is_billed", nullable = false)
    @Builder.Default
    private Boolean isBilled = Boolean.FALSE;

    @Column(name = "billing_invoice_id")
    private Long billingInvoiceId;

    @Column(name = "billing_invoice_item_id")
    private Long billingInvoiceItemId;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_authorization_status", length = 50)
    private PreAuthorizationStatus preAuthorizationStatus;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_type", length = 50)
    private String paymentType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 50)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_status", nullable = false, length = 50)
    @Builder.Default
    private CoverageStatus coverageStatus = CoverageStatus.NOT_CHECKED;

    @Column(name = "not_covered_reason", length = 100)
    private String notCoveredReason;

    @Column(name = "patient_insurance_id")
    private Long patientInsuranceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", length = 50)
    private PriceSource priceSource;

    @NotNull
    @Column(name = "is_default_service", nullable = false)
    @Builder.Default
    private Boolean isDefaultService = Boolean.FALSE;

    @NotNull
    @Column(name = "is_exempted", nullable = false)
    @Builder.Default
    private Boolean isExempted = Boolean.FALSE;

    @NotNull
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "patient_share_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal patientShareAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "insurance_share_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal insuranceShareAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "remaining_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    @Column(name = "waseel_sbs_mapping_id")
    private Long waseelSbsMappingId;

    @Column(name = "waseel_sbs_code", length = 100)
    private String waseelSbsCode;

    @NotNull
    @Column(name = "pre_authorization_required", nullable = false)
    @Builder.Default
    private Boolean preAuthorizationRequired = Boolean.FALSE;

    @Column(name = "pre_authorization_request_id")
    private Long preAuthorizationRequestId;

    @Column(name = "pre_authorization_reference_no", length = 100)
    private String preAuthorizationReferenceNo;

    /**
     * Insurance visit item that is not on the insurance price list and was
     * confirmed to bill as cash. Must not go to claims or insurance share.
     */
    public boolean isUncoveredCashItem() {
        return coverageStatus == CoverageStatus.NOT_COVERED;
    }
}