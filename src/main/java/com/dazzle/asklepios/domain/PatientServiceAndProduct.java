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

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

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

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "is_billed", nullable = false)
    @Builder.Default
    private Boolean isBilled = Boolean.FALSE;

    @Column(name = "billing_invoice_id")
    private Long billingInvoiceId;

    @Column(name = "billing_invoice_item_id")
    private Long billingInvoiceItemId;

    @Column(name = "notes")
    private String notes;

}