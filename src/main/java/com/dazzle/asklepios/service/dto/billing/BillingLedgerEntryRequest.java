package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingDebitAccount;
import com.dazzle.asklepios.domain.BillingDebitTransaction;
import com.dazzle.asklepios.domain.BillingLedger;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.BillingRefund;
import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryCategory;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryDirection;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerScope;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingLedgerEntryRequest(

        UUID transactionGroupId,
        String correlationId,
        String idempotencyKey,

        Patient patient,
        PatientEncounter encounter,

        BillingWallet wallet,
        BillingPayment payment,
        BillingPaymentTransaction paymentTransaction,

        BillingCharge charge,
        BillingChargeLine chargeLine,
        BillingChargeResponsibility chargeResponsibility,

        BillingReservation reservation,
        BillingAllocation allocation,

        BillingDebitAccount debitAccount,
        BillingDebitTransaction debitTransaction,
        BillingRefund refund,

        BillingLedgerTransactionType transactionType,
        BillingLedgerScope ledgerScope,

        BigDecimal amount,
        Currency currency,

        BigDecimal availableChange,
        BigDecimal reservedChange,
        BigDecimal consumedChange,
        BigDecimal refundedChange,
        BigDecimal debitBalanceChange,
        BigDecimal allocatedChange,
        BigDecimal outstandingChange,

        BigDecimal walletAvailableBefore,
        BigDecimal walletAvailableAfter,
        BigDecimal walletReservedBefore,
        BigDecimal walletReservedAfter,

        BigDecimal debitBalanceBefore,
        BigDecimal debitBalanceAfter,

        BigDecimal responsibilityOutstandingBefore,
        BigDecimal responsibilityOutstandingAfter,

        BillingLedgerEntryDirection entryDirection,
        BillingLedgerEntryCategory entryCategory,

        BillingLedger reversedLedger,

        String referenceType,
        Long referenceId,
        String referenceNumber,

        String description,
        String reason,

        BillingLedgerSourceChannel sourceChannel

) {
}