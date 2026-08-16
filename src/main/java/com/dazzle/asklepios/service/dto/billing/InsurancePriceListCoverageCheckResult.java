package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;

import java.io.Serializable;
import java.math.BigDecimal;

public record InsurancePriceListCoverageCheckResult(

        boolean insuranceVisit,

        boolean coveredByInsurance,

        boolean requiresCashConfirmation,

        String notCoveredReason,

        BillingItemTypes billingItemType,

        Long sourceId,

        String itemName,

        String itemCode,

        BigDecimal cashUnitPrice,

        Currency currency,

        Long patientInsuranceId,

        String warningMessage

) implements Serializable {
}
