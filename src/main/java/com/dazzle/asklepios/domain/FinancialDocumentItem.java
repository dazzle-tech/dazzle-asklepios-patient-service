package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentItemAdjustmentAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "financial_document_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDocumentItem extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private FinancialDocument document;

    @Column(name = "patient_service_product_id", nullable = false)
    private Long patientServiceProductId;

    @Column(name = "billing_charge_line_id")
    private Long billingChargeLineId;

    @Column(name = "parent_document_item_id")
    private Long parentDocumentItemId;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(name = "item_description", length = 500)
    private String itemDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_action", length = 50)
    private FinancialDocumentItemAdjustmentAction adjustmentAction;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    // ✅ Insurance Split
    @Column(name = "patient_share_amount", nullable = false)
    private BigDecimal patientShareAmount;

    @Column(name = "insurance_share_amount", nullable = false)
    private BigDecimal insuranceShareAmount;

    // ✅ Payment Tracking 💣
    @Column(name = "paid_amount", nullable = false)
    private BigDecimal paidAmount;

    @Column(name = "remaining_amount", nullable = false)
    private BigDecimal remainingAmount;

    // ✅ Insurance tracking (optional strong feature)
    @Column(name = "insurance_paid_amount")
    private BigDecimal insurancePaidAmount;

    @Column(name = "insurance_remaining_amount")
    private BigDecimal insuranceRemainingAmount;


    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FinancialDocumentItemStatus status;


    // ✅ Currency
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    // ✅ Utility
    public BigDecimal calculateRemaining(BigDecimal allocatedAmount) {
        return patientShareAmount.subtract(allocatedAmount);
    }
}