package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;

import java.time.LocalDate;

public record BillingAdjustmentResolveRequest(

        Long facilityId,

        Currency currency,

        TaxApplicableOn taxApplicableOn,

        DiscountApplicableOn discountApplicableOn,

        LocalDate pricingDate

) {
}
