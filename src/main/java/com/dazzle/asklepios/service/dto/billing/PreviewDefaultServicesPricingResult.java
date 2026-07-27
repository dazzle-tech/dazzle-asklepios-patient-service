package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record PreviewDefaultServicesPricingResult(

        Long patientId,

        Long encounterId,

        Long facilityId,

        BillingCoverageType coverageType,

        Long patientInsuranceId,

        Currency currency,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        List<PreviewDefaultServicePricingResult> items

) implements Serializable {
}
