package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

import java.math.BigDecimal;
import java.time.Instant;

public record PendingClaimInvoiceResponse(
        Long financialDocumentId,
        String documentNumber,
        Long encounterId,
        PatientEncounter encounter,
        EncounterType encounterType,
        Long patientId,
        Patient patient,
        Long payorId,
        String claimReference,
        BigDecimal totalAmount,
        String currency,
        Instant createdDate,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType,
        Integer matchingItemCount,
        BigDecimal matchingNetAmount
) {}
