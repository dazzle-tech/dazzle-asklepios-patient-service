package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;

public record FinancialDocumentNumberResponse(
        String documentNumber,
        Long sequenceNumber,
        String periodKey,
        FinancialDocumentType documentType,
        Long facilityId
) {
}
