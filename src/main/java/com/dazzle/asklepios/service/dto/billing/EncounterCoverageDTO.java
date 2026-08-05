package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;

public record EncounterCoverageDTO(
        Long encounterId,
        BillingCoverageType coverageType,
        Long patientInsuranceId,
        PaymentTypes paymentTypes,
        boolean insuranceVisit,
        boolean hasPendingPreAuthorization
) {}
