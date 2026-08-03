package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "claim_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimItem extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_request_id", nullable = false)
    private Long claimRequestId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "patient_service_product_id")
    private Long patientServiceProductId;

    @Column(name = "financial_document_item_id")
    private Long financialDocumentItemId;

    @Column(name = "billing_charge_line_id")
    private Long billingChargeLineId;

    @Column(name = "item_type", length = 50)
    private String itemType;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(name = "item_description", length = 500)
    private String itemDescription;

    @Column(name = "invoice_no", length = 100)
    private String invoiceNo;

    @Column(name = "quantity", precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "net", precision = 19, scale = 2)
    private BigDecimal net;

    @Column(name = "patient_share", precision = 19, scale = 2)
    private BigDecimal patientShare;

    @Column(name = "payer_share", precision = 19, scale = 2)
    private BigDecimal payerShare;
}
