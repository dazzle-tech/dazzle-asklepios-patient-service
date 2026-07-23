package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingCancellationResult(

        Long patientServiceProductId,

        Long chargeId,

        Long chargeLineId,

        BigDecimal walletAllocationReversedAmount,

        BigDecimal debitAllocationReversedAmount,

        BigDecimal totalReversedAllocationAmount,

        BigDecimal releasedReservationAmount,

        BigDecimal walletAvailableBalance,

        BigDecimal walletReservedBalance,

        BigDecimal walletConsumedAmount,

        boolean cancelled

) implements Serializable {
}