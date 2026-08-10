package com.dazzle.asklepios.integration.waseel.dto.claim;

import java.math.BigDecimal;
import java.time.Instant;

public record PendingClaimInvoiceResponse(
        Long financialDocumentId,
        String documentNumber,
        Long encounterId,
        Long patientId,
        Long payorId,
        String claimReference,
        BigDecimal totalAmount,
        String currency,
        Instant createdDate
) {}
