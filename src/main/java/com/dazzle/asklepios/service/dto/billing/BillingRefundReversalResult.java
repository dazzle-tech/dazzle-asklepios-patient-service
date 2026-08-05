package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingRefundReversalResult(

        Long originalRefundId,

        Long reversalRefundId,

        String reversalRefundNumber,

        BigDecimal reversedAmount,

        BigDecimal originalRefundRemainingAmount,

        BigDecimal walletAvailableBalance,

        BigDecimal walletRefundedAmount,

        BillingRefundStatus originalRefundStatus,

        BillingRefundStatus reversalStatus

) implements Serializable {
}