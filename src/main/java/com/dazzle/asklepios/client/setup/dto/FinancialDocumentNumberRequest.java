package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;

import java.time.LocalDate;

public record FinancialDocumentNumberRequest(
        Long facilityId,
        FinancialDocumentType documentType,
        LocalDate documentDate,
        Long minimumUsedSequence
) {
}
