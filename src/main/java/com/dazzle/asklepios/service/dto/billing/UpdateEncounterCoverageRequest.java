package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import jakarta.validation.constraints.NotNull;

public record UpdateEncounterCoverageRequest(
        @NotNull BillingCoverageType coverageType,
        Long patientInsuranceId
) {}
