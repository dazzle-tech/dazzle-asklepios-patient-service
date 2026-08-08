package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;

import java.io.Serializable;
import java.time.LocalDate;

public record BillingPricingResolutionRequest(

        Long facilityId,

        Long patientId,

        Long encounterId,

        BillingItemTypes billingItemType,

        Long itemId,

        Long patientInsuranceId,

        Long payerId,

        BillingCoverageType coverageType,

        Currency currency,

        LocalDate pricingDate

) implements Serializable {
}
