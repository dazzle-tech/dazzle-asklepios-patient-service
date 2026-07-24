package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record EncounterBillingItemSummary(

        Long patientServiceProductId,

        Long chargeLineId,

        String billingItemType,

        Long sourceId,

        String itemCode,

        String itemName,

        BigDecimal quantity,

        BigDecimal unitPrice,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal exemptionAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        BigDecimal patientResponsibilityAmount,

        BigDecimal insuranceResponsibilityAmount,

        BigDecimal otherPayerResponsibilityAmount,

        BigDecimal reservedAmount,

        BigDecimal allocatedAmount,

        BigDecimal outstandingAmount,

        Boolean exempted,

        Currency currency,

        BillingChargeLineStatus status,

        List<BillingResponsibilitySummary> responsibilities

) implements Serializable {
}
