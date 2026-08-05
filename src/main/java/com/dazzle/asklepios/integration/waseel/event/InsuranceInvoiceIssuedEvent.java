package com.dazzle.asklepios.integration.waseel.event;

public record InsuranceInvoiceIssuedEvent(
        Long encounterId,
        Long financialDocumentId
) {}
