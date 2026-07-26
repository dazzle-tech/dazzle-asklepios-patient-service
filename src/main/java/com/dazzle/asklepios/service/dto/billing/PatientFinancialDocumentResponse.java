package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record PatientFinancialDocumentResponse(

        Long id,

        String documentNumber,

        FinancialDocumentType documentType,

        FinancialDocumentSubtype documentSubtype,

        FinancialDocumentStatus status,

        Long patientId,

        Long encounterId,

        BigDecimal totalAmount,

        Currency currency,

        String eligibilityReference,

        String claimReference,

        Instant createdDate

) implements Serializable {
}
