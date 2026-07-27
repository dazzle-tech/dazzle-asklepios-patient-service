package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.List;

public record PreviewDefaultServicesPricingRequest(

        @NotNull
        Long patientId,

        @NotNull
        Long facilityId,

        @NotNull
        Currency currency,

        @NotNull
        BillingCoverageType coverageType,

        Long patientInsuranceId,

        @NotEmpty
        List<@Valid PrepareDefaultServiceItem> items

) implements Serializable {
}
