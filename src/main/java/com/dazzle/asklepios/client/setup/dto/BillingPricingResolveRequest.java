package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;

import java.io.Serializable;
import java.time.LocalDate;

public record BillingPricingResolveRequest(

        Long facilityId,

        Long patientId,

        Long encounterId,

        BillingItemTypes billingItemType,

        Long sourceId,

        Long patientInsuranceId,

        Long payerId,

        BillingCoverageType coverageType,

        Currency currency,

        TaxApplicableOn taxApplicableOn,

        DiscountApplicableOn discountApplicableOn,

        LocalDate pricingDate,

        EncounterType visitType

) implements Serializable {
}