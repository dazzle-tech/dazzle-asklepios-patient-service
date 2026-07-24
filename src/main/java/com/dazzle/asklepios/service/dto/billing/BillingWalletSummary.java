package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingWalletStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingWalletSummary(

        Long walletId,

        BigDecimal creditedAmount,

        BigDecimal availableBalance,

        BigDecimal reservedBalance,

        BigDecimal consumedAmount,

        BigDecimal refundedAmount,

        Currency currency,

        BillingWalletStatus status

) implements Serializable {
}
