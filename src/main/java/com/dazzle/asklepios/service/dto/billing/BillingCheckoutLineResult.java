package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingCheckoutLineResult(

        Long chargeLineId,

        Long patientServiceProductId,

        Long patientResponsibilityId,

        BigDecimal patientResponsibilityAmount,

        BigDecimal reservedAllocationAmount,

        BigDecimal availableWalletAllocationAmount,

        BigDecimal debitAllocationAmount,

        BigDecimal patientOutstandingAfter,

        boolean patientSettled

) implements Serializable {
}