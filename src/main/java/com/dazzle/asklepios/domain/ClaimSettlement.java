package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;
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

/**
 * Statement of account created before claim submission.
 * Invoices selected together share one settlement number. Existing claim rows are not migrated into this table.
 */
@Entity
@Table(name = "claim_settlement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSettlement extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_no", nullable = false, unique = true, length = 50)
    private String settlementNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ClaimSettlementStatus status;

    @Column(name = "payor_id")
    private Long payorId;

    @Column(name = "payer_nphies_id", length = 100)
    private String payerNphiesId;

    @Column(name = "payer_name", length = 255)
    private String payerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", length = 50)
    private WaseelClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_sub_type", length = 50)
    private WaseelClaimSubType claimSubType;

    @Column(name = "invoice_count", nullable = false)
    private Integer invoiceCount;

    @Column(name = "total_invoice_amount", precision = 19, scale = 2)
    private BigDecimal totalInvoiceAmount;

    @Column(name = "total_claim_amount", precision = 19, scale = 2)
    private BigDecimal totalClaimAmount;
}
