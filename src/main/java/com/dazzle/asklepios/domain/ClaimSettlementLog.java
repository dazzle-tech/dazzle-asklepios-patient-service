package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementLogAction;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
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

/**
 * Append-only history of an invoice moving in and out of statements of account.
 * Rows are never updated, so a rejected invoice that is grouped again keeps its full trail.
 */
@Entity
@Table(name = "claim_settlement_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSettlementLog extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "settlement_no", length = 50)
    private String settlementNo;

    @Column(name = "financial_document_id", nullable = false)
    private Long financialDocumentId;

    @Column(name = "claim_request_id")
    private Long claimRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private ClaimSettlementLogAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", length = 50)
    private ClaimStatus claimStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
