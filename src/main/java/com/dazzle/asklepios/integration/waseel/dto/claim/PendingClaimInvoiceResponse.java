package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.Patient;

import java.math.BigDecimal;
import java.time.Instant;

public record PendingClaimInvoiceResponse(
        Long financialDocumentId,
        String documentNumber,
        Long encounterId,
        Long patientId,
        Patient patient,
        Long payorId,
        String claimReference,
        BigDecimal totalAmount,
        String currency,
        Instant createdDate
) {}
