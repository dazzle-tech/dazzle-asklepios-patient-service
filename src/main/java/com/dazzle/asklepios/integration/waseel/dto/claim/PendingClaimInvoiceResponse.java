package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;

import java.math.BigDecimal;
import java.time.Instant;

public record PendingClaimInvoiceResponse(
        Long financialDocumentId,
        String documentNumber,
        Long encounterId,
        PatientEncounter encounter,
        Long patientId,
        Patient patient,
        Long payorId,
        String claimReference,
        BigDecimal totalAmount,
        String currency,
        Instant createdDate
) {}
