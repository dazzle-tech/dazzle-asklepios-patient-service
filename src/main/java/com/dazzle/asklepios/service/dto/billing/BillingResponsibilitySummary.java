package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsibilityRole;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingResponsibilitySummary(

        Long responsibilityId,

        ResponsiblePartyType responsiblePartyType,

        ResponsibilityRole responsibilityRole,

        Long payerId,

        Long patientInsuranceId,

        BigDecimal responsibilityAmount,

        BigDecimal allocatedAmount,

        BigDecimal outstandingAmount,

        BigDecimal coveragePercentage,

        BigDecimal deductibleAmount,

        BigDecimal copayAmount,

        BigDecimal coinsuranceAmount,

        BigDecimal nonCoveredAmount,

        Currency currency,

        BillingResponsibilityStatus status

) implements Serializable {
}
