package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;

import java.io.Serializable;
import java.util.List;

public record GenerateInvoiceResult(

        Long encounterId,

        EncounterBillingStatus billingStatus,

        String coverageType,

        List<PatientFinancialDocumentResponse> invoices

) implements Serializable {
}
