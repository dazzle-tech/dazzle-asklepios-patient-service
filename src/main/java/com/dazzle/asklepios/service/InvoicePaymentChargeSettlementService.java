package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingDebitAccount;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.enumeration.billing.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingDebitAccountRepository;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.BillingPaymentTransactionRepository;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InvoicePaymentChargeSettlementService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    InvoicePaymentChargeSettlementService.class
            );

    private static final int MONEY_SCALE = 4;

    private static final String REVERSAL_REASON =
            "Invoice payment converted debit allocation to payment allocation.";

    private static final String REVERSED_BY = "INVOICE_PAYMENT";

    private static final EnumSet<BillingChargeStatus>
            EXCLUDED_CHARGE_STATUSES =
            EnumSet.of(
                    BillingChargeStatus.CANCELLED,
                    BillingChargeStatus.REVERSED
            );

    private static final EnumSet<BillingAllocationStatus>
            ACTIVE_ALLOCATION_STATUSES =
            EnumSet.of(
                    BillingAllocationStatus.ACTIVE,
                    BillingAllocationStatus.PARTIALLY_REVERSED
            );

    private final BillingPaymentRepository billingPaymentRepository;

    private final BillingPaymentTransactionRepository
            billingPaymentTransactionRepository;

    private final BillingChargeRepository billingChargeRepository;

    private final BillingAllocationRepository billingAllocationRepository;

    private final BillingDebitAccountRepository billingDebitAccountRepository;

    private final BillingAllocationService billingAllocationService;

    private final BillingDebitService billingDebitService;

    private final BillingChargeService billingChargeService;

    @Transactional(rollbackFor = Exception.class)
    public void settleFromInvoicePayment(
            Long encounterId,
            Long paymentId,
            Long paymentTransactionId,
            BigDecimal amountToCollect,
            String requestId
    ) {
        if (encounterId == null
                || paymentId == null
                || amountToCollect == null
                || money(amountToCollect).signum() <= 0) {
            return;
        }

        BillingPayment payment =
                billingPaymentRepository
                        .findById(paymentId)
                        .orElse(null);

        if (payment == null) {
            return;
        }

        BillingPaymentTransaction paymentTransaction =
                paymentTransactionId == null
                        ? null
                        : billingPaymentTransactionRepository
                                .findById(paymentTransactionId)
                                .orElse(null);

        BillingCharge charge =
                billingChargeRepository
                        .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                                encounterId,
                                EXCLUDED_CHARGE_STATUSES
                        )
                        .orElse(null);

        if (charge == null) {
            return;
        }

        List<BillingAllocation> debitAllocations =
                billingAllocationRepository
                        .findAllByEncounter_IdAndCharge_IdAndStatusInOrderByAllocationDateAscIdAsc(
                                encounterId,
                                charge.getId(),
                                ACTIVE_ALLOCATION_STATUSES
                        )
                        .stream()
                        .filter(allocation ->
                                allocation.getAllocationSourceType()
                                        == AllocationSourceType.DEBIT
                        )
                        .filter(allocation ->
                                money(
                                        allocation
                                                .getRemainingAllocatedAmount()
                                ).signum() > 0
                        )
                        .toList();

        if (debitAllocations.isEmpty()) {
            return;
        }

        BigDecimal paymentLeft = money(amountToCollect);
        BigDecimal totalDebitSettled = BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
        Set<Long> chargeIds = new HashSet<>();

        for (BillingAllocation debitAllocation : debitAllocations) {
            if (paymentLeft.signum() <= 0) {
                break;
            }

            BigDecimal convertibleAmount =
                    paymentLeft.min(
                            money(
                                    debitAllocation
                                            .getRemainingAllocatedAmount()
                            )
                    );

            if (convertibleAmount.signum() <= 0) {
                continue;
            }

            Long responsibilityId =
                    debitAllocation
                            .getChargeResponsibility()
                            .getId();

            billingAllocationService.reverseDebitAllocationBalances(
                    debitAllocation.getId(),
                    convertibleAmount,
                    REVERSAL_REASON,
                    REVERSED_BY
            );

            billingAllocationService.allocateFromPayment(
                    responsibilityId,
                    payment,
                    paymentTransaction,
                    convertibleAmount,
                    requestId + ":A" + debitAllocation.getId(),
                    BillingLedgerSourceChannel.SYSTEM
            );

            paymentLeft = paymentLeft.subtract(convertibleAmount);
            totalDebitSettled =
                    totalDebitSettled.add(convertibleAmount);
            chargeIds.add(charge.getId());
        }

        if (totalDebitSettled.signum() <= 0) {
            return;
        }

        BillingDebitAccount debitAccount =
                billingDebitAccountRepository
                        .findByPatient_IdAndCurrency(
                                payment.getPatient().getId(),
                                payment.getCurrency()
                        )
                        .orElse(null);

        if (debitAccount != null
                && money(
                        debitAccount.getCurrentDebitBalance()
                ).signum() > 0) {
            billingDebitService.settleDebitFromAppliedPayment(
                    debitAccount.getId(),
                    totalDebitSettled,
                    payment,
                    paymentTransaction,
                    requestId + ":DEBIT_SETTLEMENT",
                    BillingLedgerSourceChannel.SYSTEM
            );
        }

        for (Long chargeId : chargeIds) {
            BillingCharge chargeToRecalculate = new BillingCharge();
            chargeToRecalculate.setId(chargeId);

            billingChargeService.recalculateChargeTotals(
                    BillingProcessingContext.builder()
                            .charge(chargeToRecalculate)
                            .idempotencyKey(
                                    "INVOICE_PAYMENT_SETTLEMENT:CHARGE:"
                                            + chargeId
                            )
                            .build()
            );
        }

        LOG.info(
                "[INVOICE_PAYMENT_SETTLEMENT] encounterId={} paymentId={} "
                        + "convertedDebit={} remainingPayment={}",
                encounterId,
                paymentId,
                totalDebitSettled,
                paymentLeft
        );
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }
}
