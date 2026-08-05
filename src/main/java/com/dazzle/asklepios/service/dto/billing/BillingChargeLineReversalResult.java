package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingChargeLineReversalResult(

        BigDecimal walletAllocationReversedAmount,

        BigDecimal debitAllocationReversedAmount,

        BigDecimal totalReversedAmount

) implements Serializable {
}