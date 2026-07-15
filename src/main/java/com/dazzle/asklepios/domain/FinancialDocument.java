package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.Relations;
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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
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
import java.time.Instant;
import java.util.EnumSet;

@Entity
@Table(name = "financial_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ document number (INV-00000001)
    @Column(
            name = "document_number",
            insertable = false,
            updatable = false
    )
    private String documentNumber;

    // ✅ INVOICE / CREDIT_NOTE / DEBIT_NOTE / RECEIPT
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private FinancialDocumentType documentType;

    // ✅ lifecycle
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FinancialDocumentStatus status;

    // ✅ relations
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    // ✅ parent for CN / DN
    @Column(name = "parent_document_id")
    private Long parentDocumentId;

    // ✅ amounts
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10)
    private Currency currency;

    @Column(name = "created_date")
    private Instant createdDate;

    public boolean isEditable() {
        return EnumSet.of(
                FinancialDocumentStatus.DRAFT,
                FinancialDocumentStatus.ISSUED
        ).contains(this.status);
    }

}