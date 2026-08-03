package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;

import java.io.Serializable;
import java.math.BigDecimal;

public record InsurancePayerReceivablesSummaryResponse(

        Long payerId,

        String payerName,

        Currency currency,

        BigDecimal totalBilled,

        BigDecimal totalReceived,

        BigDecimal outstandingBalance,

        long pendingClaims,

        long paidClaims,

        long partiallyPaidClaims,

        long rejectedClaims,

        long cancelledClaims,

        String overallStatus

) implements Serializable {
}
