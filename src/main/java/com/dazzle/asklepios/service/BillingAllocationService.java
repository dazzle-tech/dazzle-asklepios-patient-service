package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingDebitTransaction;
import com.dazzle.asklepios.domain.BillingLedger;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryCategory;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryDirection;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerScope;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerTransactionType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingReservationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.BillingDebitTransactionRepository;
import com.dazzle.asklepios.repository.BillingLedgerRepository;
import com.dazzle.asklepios.repository.BillingReservationRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingAllocationResult;
import com.dazzle.asklepios.service.dto.billing.BillingAllocationReversalResult;
import com.dazzle.asklepios.service.dto.billing.BillingLedgerEntryRequest;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingAllocationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingAllocationService.class
            );

    private static final String ENTITY_NAME =
            "billingAllocation";

    private static final int MONEY_SCALE = 4;

    private final BillingAllocationRepository
            billingAllocationRepository;

    private final BillingReservationRepository
            billingReservationRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final BillingChargeLineRepository
            billingChargeLineRepository;

    private final BillingChargeRepository
            billingChargeRepository;

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final BillingLedgerRepository
            billingLedgerRepository;

    private final BillingWalletService
            billingWalletService;

    private final BillingLedgerService
            billingLedgerService;

    private final BillingDebitTransactionRepository
            billingDebitTransactionRepository;

    /*
     * ============================================================
     * ALLOCATION FROM RESERVATION
     * ============================================================
     */

    @Transactional(rollbackFor = Exception.class)
    public BillingAllocationResult allocateFromReservation(
            Long reservationId,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateReservationAllocationInput(
                reservationId,
                requestedAmount,
                requestId,
                sourceChannel
        );

        String idempotencyKey =
                "ALLOCATION:RESERVATION:"
                        + reservationId
                        + ":"
                        + requestId.trim();

        BillingAllocation existing =
                billingAllocationRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            BillingWallet wallet =
                    billingWalletService.lockWallet(
                            existing.getPatient().getId(),
                            existing.getCurrency()
                    );

            return buildResult(
                    existing,
                    wallet
            );
        }

        /*
         * Repository findById must be PESSIMISTIC_WRITE.
         */
        BillingReservation reservation =
                billingReservationRepository
                        .findById(reservationId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing reservation not found with id "
                                                + reservationId,
                                        ENTITY_NAME,
                                        "reservation.notfound"
                                )
                        );

        if (reservation.getStatus()
                != BillingReservationStatus.ACTIVE) {
            throw new BadRequestAlertException(
                    "Only active reservations can be allocated.",
                    ENTITY_NAME,
                    "reservation.notActive"
            );
        }

        BillingChargeResponsibility responsibility =
                lockResponsibility(
                        reservation
                                .getChargeResponsibility()
                                .getId()
                );

        validatePatientResponsibility(
                responsibility
        );

        BillingChargeLine chargeLine =
                lockChargeLine(
                        reservation
                                .getChargeLine()
                                .getId()
                );

        BillingCharge charge =
                lockCharge(
                        reservation
                                .getCharge()
                                .getId()
                );

        PatientServiceAndProduct item =
                loadPatientServiceProduct(
                        reservation
                                .getPatientServiceProduct()
                                .getId()
                );

        BillingWallet wallet =
                billingWalletService.lockWallet(
                        reservation.getPatient().getId(),
                        reservation.getCurrency()
                );

        BigDecimal walletAvailableBefore =
                money(
                        wallet.getAvailableBalance()
                );

        BigDecimal walletReservedBefore =
                money(
                        wallet.getReservedBalance()
                );

        BigDecimal responsibilityOutstandingBefore =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal amount =
                resolveReservationAllocationAmount(
                        requestedAmount,
                        reservation,
                        responsibility,
                        chargeLine,
                        wallet
                );

        /*
         * Wallet:
         * reserved -> consumed
         */
        BillingWallet updatedWallet =
                billingWalletService.consumeReserved(
                        wallet,
                        amount
                );

        /*
         * Reservation:
         * remainingReserved decreases
         * consumed increases
         */
        updateReservationAfterAllocation(
                reservation,
                amount
        );

        updateResponsibilityAfterAllocation(
                responsibility,
                amount
        );

        updateChargeLineAfterReservationAllocation(
                chargeLine,
                amount
        );

        updateChargeAfterAllocation(
                charge,
                amount
        );

        updatePatientItem(
                item,
                responsibility
        );

        BillingAllocation allocation =
                createReservationAllocation(
                        reservation,
                        responsibility,
                        chargeLine,
                        charge,
                        item,
                        amount,
                        idempotencyKey
                );

        recordAllocationLedger(
                allocation,
                updatedWallet,
                reservation,
                responsibility,
                chargeLine,
                charge,
                amount,
                walletAvailableBefore,
                walletReservedBefore,
                responsibilityOutstandingBefore,
                requestId,
                sourceChannel
        );

        LOG.info(
                "[ALLOCATE_RESERVATION] Allocation completed "
                        + "allocationId={} reservationId={} "
                        + "amount={} responsibilityOutstanding={}",
                allocation.getId(),
                reservation.getId(),
                amount,
                responsibility.getOutstandingAmount()
        );

        return buildResult(
                allocation,
                updatedWallet
        );
    }

    /*
     * ============================================================
     * DIRECT ALLOCATION FROM AVAILABLE WALLET
     * ============================================================
     */

    @Transactional(rollbackFor = Exception.class)
    public BillingAllocationResult allocateFromAvailableWallet(
            Long chargeResponsibilityId,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateAvailableWalletAllocationInput(
                chargeResponsibilityId,
                requestedAmount,
                requestId,
                sourceChannel
        );

        String idempotencyKey =
                "ALLOCATION:WALLET_AVAILABLE:RESPONSIBILITY:"
                        + chargeResponsibilityId
                        + ":"
                        + requestId.trim();

        BillingAllocation existing =
                billingAllocationRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            BillingWallet existingWallet =
                    billingWalletService.lockWallet(
                            existing.getPatient().getId(),
                            existing.getCurrency()
                    );

            return buildResult(
                    existing,
                    existingWallet
            );
        }

        BillingChargeResponsibility responsibility =
                lockResponsibility(
                        chargeResponsibilityId
                );

        validatePatientResponsibility(
                responsibility
        );

        BillingChargeLine chargeLine =
                lockChargeLine(
                        responsibility
                                .getChargeLine()
                                .getId()
                );

        BillingCharge charge =
                lockCharge(
                        responsibility
                                .getCharge()
                                .getId()
                );

        PatientServiceAndProduct item =
                loadPatientServiceProduct(
                        responsibility
                                .getPatientServiceProduct()
                                .getId()
                );

        BillingWallet wallet =
                billingWalletService.lockWallet(
                        responsibility.getPatient().getId(),
                        responsibility.getCurrency()
                );

        BigDecimal walletAvailableBefore =
                money(
                        wallet.getAvailableBalance()
                );

        BigDecimal walletReservedBefore =
                money(
                        wallet.getReservedBalance()
                );

        BigDecimal responsibilityOutstandingBefore =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal amount =
                resolveAvailableWalletAllocationAmount(
                        requestedAmount,
                        wallet,
                        responsibility,
                        chargeLine
                );

        /*
         * Wallet:
         * available -> consumed
         */
        BillingWallet updatedWallet =
                billingWalletService.consumeAvailable(
                        wallet,
                        amount
                );

        updateResponsibilityAfterAllocation(
                responsibility,
                amount
        );

        /*
         * Important:
         * reservedAmount is not changed.
         */
        updateChargeLineAfterAvailableAllocation(
                chargeLine,
                amount
        );

        updateChargeAfterAllocation(
                charge,
                amount
        );

        updatePatientItem(
                item,
                responsibility
        );

        UUID transactionGroupId =
                UUID.randomUUID();

        BillingAllocation allocation =
                createAvailableWalletAllocation(
                        updatedWallet,
                        responsibility,
                        chargeLine,
                        charge,
                        item,
                        amount,
                        idempotencyKey,
                        transactionGroupId
                );

        recordAvailableWalletAllocationLedger(
                allocation,
                updatedWallet,
                responsibility,
                chargeLine,
                charge,
                amount,
                walletAvailableBefore,
                walletReservedBefore,
                responsibilityOutstandingBefore,
                requestId,
                sourceChannel
        );

        LOG.info(
                "[ALLOCATE_AVAILABLE] Allocation completed "
                        + "allocationId={} responsibilityId={} "
                        + "amount={} walletAvailableBefore={} "
                        + "walletAvailableAfter={}",
                allocation.getId(),
                responsibility.getId(),
                amount,
                walletAvailableBefore,
                updatedWallet.getAvailableBalance()
        );

        return buildResult(
                allocation,
                updatedWallet
        );
    }

    /*
     * ============================================================
     * ALLOCATION FROM CONFIRMED PAYMENT
     * ============================================================
     */

    @Transactional(rollbackFor = Exception.class)
    public BillingAllocationResult allocateFromPayment(
            Long chargeResponsibilityId,
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validatePaymentAllocationInput(
                chargeResponsibilityId,
                payment,
                paymentTransaction,
                requestedAmount,
                requestId,
                sourceChannel
        );

        String idempotencyKey =
                buildPaymentAllocationIdempotencyKey(
                        payment.getId(),
                        chargeResponsibilityId,
                        requestId
                );

        BillingAllocation existing =
                billingAllocationRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            BillingWallet existingWallet =
                    billingWalletService.lockWallet(
                            existing.getPatient().getId(),
                            existing.getCurrency()
                    );

            return buildResult(
                    existing,
                    existingWallet
            );
        }

        BillingChargeResponsibility responsibility =
                lockResponsibility(
                        chargeResponsibilityId
                );

        validatePatientResponsibility(
                responsibility
        );

        if (!responsibility.getPatient()
                .getId()
                .equals(
                        payment.getPatient().getId()
                )) {
            throw new BadRequestAlertException(
                    "Payment does not belong to the responsibility patient.",
                    ENTITY_NAME,
                    "payment.patient.mismatch"
            );
        }

        BillingChargeLine chargeLine =
                lockChargeLine(
                        responsibility
                                .getChargeLine()
                                .getId()
                );

        BillingCharge charge =
                lockCharge(
                        responsibility
                                .getCharge()
                                .getId()
                );

        PatientServiceAndProduct item =
                loadPatientServiceProduct(
                        responsibility
                                .getPatientServiceProduct()
                                .getId()
                );

        BillingWallet wallet =
                billingWalletService.lockWallet(
                        responsibility.getPatient().getId(),
                        responsibility.getCurrency()
                );

        BigDecimal responsibilityOutstandingBefore =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal amount =
                resolvePaymentAllocationAmount(
                        requestedAmount,
                        responsibility,
                        chargeLine
                );

        updateResponsibilityAfterAllocation(
                responsibility,
                amount
        );

        updateChargeLineAfterAvailableAllocation(
                chargeLine,
                amount
        );

        updateChargeAfterAllocation(
                charge,
                amount
        );

        updatePatientItem(
                item,
                responsibility
        );

        UUID transactionGroupId = UUID.randomUUID();

        BillingAllocation allocation =
                createPaymentAllocation(
                        payment,
                        paymentTransaction,
                        responsibility,
                        chargeLine,
                        charge,
                        item,
                        amount,
                        idempotencyKey,
                        transactionGroupId
                );

        recordPaymentAllocationLedger(
                allocation,
                responsibility,
                chargeLine,
                charge,
                amount,
                responsibilityOutstandingBefore,
                requestId,
                sourceChannel
        );

        LOG.info(
                "[ALLOCATE_PAYMENT] Allocation completed "
                        + "allocationId={} paymentId={} responsibilityId={} "
                        + "amount={} responsibilityOutstanding={}",
                allocation.getId(),
                payment.getId(),
                responsibility.getId(),
                amount,
                responsibility.getOutstandingAmount()
        );

        return buildResult(
                allocation,
                wallet
        );
    }

    /*
     * ============================================================
     * ALLOCATION REVERSAL
     * ============================================================
     */
    @Transactional(rollbackFor = Exception.class)
    public BillingAllocationResult allocateFromDebitTransaction(
            Long debitTransactionId,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateDebitAllocationInput(
                debitTransactionId,
                requestedAmount,
                requestId,
                sourceChannel
        );

        String idempotencyKey =
                "ALLOCATION:DEBIT_TRANSACTION:"
                        + debitTransactionId
                        + ":"
                        + requestId.trim();

        BillingAllocation existing =
                billingAllocationRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            return buildResult(
                    existing,
                    null
            );
        }

        BillingDebitTransaction debitTransaction =
                billingDebitTransactionRepository
                        .findById(debitTransactionId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing debit transaction not found with id "
                                                + debitTransactionId,
                                        ENTITY_NAME,
                                        "debitTransaction.notfound"
                                )
                        );

        validateDebitTransaction(
                debitTransaction
        );

        if (debitTransaction.getChargeResponsibility() == null
                || debitTransaction
                .getChargeResponsibility()
                .getId() == null) {

            throw new BadRequestAlertException(
                    "Debit transaction is not linked to a billing responsibility.",
                    ENTITY_NAME,
                    "debitTransaction.responsibility.missing"
            );
        }

        BillingChargeResponsibility responsibility =
                lockResponsibility(
                        debitTransaction
                                .getChargeResponsibility()
                                .getId()
                );

        validatePatientResponsibility(
                responsibility
        );

        BillingChargeLine chargeLine =
                lockChargeLine(
                        responsibility
                                .getChargeLine()
                                .getId()
                );

        BillingCharge charge =
                lockCharge(
                        responsibility
                                .getCharge()
                                .getId()
                );

        PatientServiceAndProduct item =
                loadPatientServiceProduct(
                        responsibility
                                .getPatientServiceProduct()
                                .getId()
                );

        validateDebitTransactionRelations(
                debitTransaction,
                responsibility,
                chargeLine,
                charge,
                item
        );

        BigDecimal responsibilityOutstandingBefore =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal amount =
                resolveDebitAllocationAmount(
                        debitTransaction,
                        requestedAmount,
                        responsibility,
                        chargeLine
                );

        updateResponsibilityAfterAllocation(
                responsibility,
                amount
        );

        /*
         * Debit allocation does not change reservedAmount.
         */
        updateChargeLineAfterAvailableAllocation(
                chargeLine,
                amount
        );

        updateChargeAfterAllocation(
                charge,
                amount
        );

        updatePatientItem(
                item,
                responsibility
        );

        BillingAllocation allocation =
                createDebitAllocation(
                        debitTransaction,
                        responsibility,
                        chargeLine,
                        charge,
                        item,
                        amount,
                        idempotencyKey
                );

        recordDebitAllocationLedger(
                allocation,
                debitTransaction,
                responsibility,
                chargeLine,
                charge,
                amount,
                responsibilityOutstandingBefore,
                requestId,
                sourceChannel
        );

        LOG.info(
                "[ALLOCATE_DEBIT] Debit transaction allocated "
                        + "allocationId={} debitTransactionId={} "
                        + "responsibilityId={} amount={} outstandingAfter={}",
                allocation.getId(),
                debitTransaction.getId(),
                responsibility.getId(),
                amount,
                responsibility.getOutstandingAmount()
        );

        return buildResult(
                allocation,
                null
        );
    }

    private void recordDebitAllocationLedger(
            BillingAllocation allocation,
            BillingDebitTransaction debitTransaction,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            BigDecimal amount,
            BigDecimal responsibilityOutstandingBefore,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        /*
         * Allocation consumes responsibility against an existing debit balance.
         * The debit account balance was already updated by createDebit(), so
         * debitBalanceChange stays zero and before/after must reflect the
         * current account balance (not the debit transaction's creation
         * snapshot).
         */
        BigDecimal debitBalanceSnapshot =
                money(
                        debitTransaction
                                .getDebitAccount()
                                .getCurrentDebitBalance()
                );

        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        allocation.getTransactionGroupId(),
                        requestId.trim(),
                        allocation.getIdempotencyKey()
                                + ":LEDGER",

                        allocation.getPatient(),
                        allocation.getEncounter(),

                        null,
                        null,
                        null,

                        charge,
                        chargeLine,
                        responsibility,

                        null,
                        allocation,

                        debitTransaction.getDebitAccount(),
                        debitTransaction,
                        null,

                        BillingLedgerTransactionType
                                .ALLOCATION_CREATED,

                        BillingLedgerScope.ALLOCATION,

                        amount,
                        allocation.getCurrency(),

                        zero(),
                        zero(),
                        zero(),
                        zero(),

                        zero(),

                        amount,
                        amount.negate(),

                        null,
                        null,
                        null,
                        null,

                        debitBalanceSnapshot,
                        debitBalanceSnapshot,

                        responsibilityOutstandingBefore,
                        money(
                                responsibility.getOutstandingAmount()
                        ),

                        BillingLedgerEntryDirection.DEBIT,
                        BillingLedgerEntryCategory.BUSINESS,

                        null,

                        "BILLING_DEBIT_TRANSACTION",
                        debitTransaction.getId(),
                        debitTransaction.getTransactionNumber(),

                        "Patient responsibility transferred to the debit account.",

                        debitTransaction.getReason(),

                        sourceChannel
                )
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public BillingAllocationReversalResult reverseAllocation(
            Long allocationId,
            BigDecimal requestedAmount,
            String reason,
            String reversedBy,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateReversalInput(
                allocationId,
                requestedAmount,
                reason,
                reversedBy,
                requestId,
                sourceChannel
        );

        BillingAllocation allocation =
                billingAllocationRepository
                        .findById(allocationId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing allocation not found with id "
                                                + allocationId,
                                        ENTITY_NAME,
                                        "allocation.notfound"
                                )
                        );
        if (allocation.getAllocationSourceType()
                == AllocationSourceType.DEBIT) {

            throw new BadRequestAlertException(
                    "Debit allocation must be reversed through BillingDebitService to keep debit-account balance consistent.",
                    ENTITY_NAME,
                    "debitAllocation.useDebitReversal"
            );
        }
        if (allocation.getStatus()
                == BillingAllocationStatus.REVERSED) {
            BillingWallet wallet =
                    billingWalletService.lockWallet(
                            allocation.getPatient().getId(),
                            allocation.getCurrency()
                    );

            return buildReversalResult(
                    allocation,
                    zero(),
                    wallet
            );
        }

        if (allocation.getStatus()
                == BillingAllocationStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled allocation cannot be reversed.",
                    ENTITY_NAME,
                    "allocation.cancelled"
            );
        }

        String reversalIdempotencyKey =
                "ALLOCATION_REVERSAL:"
                        + allocationId
                        + ":"
                        + requestId.trim();

        BillingLedger existingReversal =
                billingLedgerRepository
                        .findByIdempotencyKey(
                                reversalIdempotencyKey
                                        + ":LEDGER"
                        )
                        .orElse(null);

        if (existingReversal != null) {
            BillingAllocation refreshed =
                    billingAllocationRepository
                            .findById(allocationId)
                            .orElseThrow(() ->
                                    new NotFoundAlertException(
                                            "Billing allocation not found.",
                                            ENTITY_NAME,
                                            "allocation.notfound"
                                    )
                            );

            BillingWallet wallet =
                    billingWalletService.lockWallet(
                            refreshed.getPatient().getId(),
                            refreshed.getCurrency()
                    );

            return buildReversalResult(
                    refreshed,
                    existingReversal.getAmount(),
                    wallet
            );
        }

        BigDecimal reversalAmount =
                resolveReversalAmount(
                        allocation,
                        requestedAmount
                );

        BillingChargeResponsibility responsibility =
                lockResponsibility(
                        allocation
                                .getChargeResponsibility()
                                .getId()
                );

        BillingChargeLine chargeLine =
                lockChargeLine(
                        allocation
                                .getChargeLine()
                                .getId()
                );

        BillingCharge charge =
                lockCharge(
                        allocation
                                .getCharge()
                                .getId()
                );

        PatientServiceAndProduct item =
                loadPatientServiceProduct(
                        allocation
                                .getPatientServiceProduct()
                                .getId()
                );

        BillingWallet wallet =
                billingWalletService.lockWallet(
                        allocation.getPatient().getId(),
                        allocation.getCurrency()
                );

        BigDecimal walletAvailableBefore =
                money(
                        wallet.getAvailableBalance()
                );

        BigDecimal walletReservedBefore =
                money(
                        wallet.getReservedBalance()
                );

        BigDecimal responsibilityOutstandingBefore =
                money(
                        responsibility.getOutstandingAmount()
                );

        /*
         * Both supported allocation sources have already moved money
         * to wallet consumed balance.
         *
         * Reversal returns:
         * consumed -> available
         */
        BillingWallet updatedWallet =
                billingWalletService
                        .reverseConsumedToAvailable(
                                wallet,
                                reversalAmount
                        );

        reverseResponsibility(
                responsibility,
                reversalAmount
        );

        reverseChargeLine(
                chargeLine,
                reversalAmount
        );

        reverseCharge(
                charge,
                reversalAmount
        );

        reverseAllocationAmounts(
                allocation,
                reversalAmount,
                reason,
                reversedBy
        );

        updatePatientItemAfterReversal(
                item,
                responsibility
        );

        BillingLedger originalLedger =
                billingLedgerRepository
                        .findFirstByAllocation_IdAndTransactionTypeOrderByIdAsc(
                                allocation.getId(),
                                BillingLedgerTransactionType
                                        .ALLOCATION_CREATED
                        )
                        .orElse(null);

        recordReversalLedger(
                allocation,
                updatedWallet,
                responsibility,
                chargeLine,
                charge,
                reversalAmount,
                walletAvailableBefore,
                walletReservedBefore,
                responsibilityOutstandingBefore,
                reversalIdempotencyKey,
                requestId,
                reason,
                sourceChannel,
                originalLedger
        );

        LOG.info(
                "[REVERSE] Allocation reversed "
                        + "allocationId={} sourceType={} "
                        + "reversedAmount={} remainingAllocated={} "
                        + "status={} availableAfter={} consumedAfter={}",
                allocation.getId(),
                allocation.getAllocationSourceType(),
                reversalAmount,
                allocation.getRemainingAllocatedAmount(),
                allocation.getStatus(),
                updatedWallet.getAvailableBalance(),
                updatedWallet.getConsumedAmount()
        );

        return buildReversalResult(
                allocation,
                reversalAmount,
                updatedWallet
        );
    }

    /*
     * ============================================================
     * CREATE ALLOCATION ROWS
     * ============================================================
     */

    private BillingAllocation createReservationAllocation(
            BillingReservation reservation,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            PatientServiceAndProduct item,
            BigDecimal amount,
            String idempotencyKey
    ) {
        BillingAllocation allocation =
                BillingAllocation.builder()
                        .allocationNumber(
                                generateAllocationNumber()
                        )
                        .charge(charge)
                        .chargeLine(chargeLine)
                        .chargeResponsibility(
                                responsibility
                        )
                        .patientServiceProduct(item)
                        .patient(
                                reservation.getPatient()
                        )
                        .encounter(
                                reservation.getEncounter()
                        )
                        .allocationSourceType(
                                AllocationSourceType.RESERVATION
                        )
                        .reservation(reservation)
                        .payment(
                                reservation.getPayment()
                        )
                        .paymentTransaction(
                                reservation
                                        .getPaymentTransaction()
                        )
                        .debitTransactionId(null)
                        .sourceReferenceType(
                                "BILLING_RESERVATION"
                        )
                        .sourceReferenceId(
                                reservation.getId()
                        )
                        .sourceReferenceNumber(
                                reservation
                                        .getReservationNumber()
                        )
                        .allocatedAmount(amount)
                        .remainingAllocatedAmount(amount)
                        .reversedAmount(zero())
                        .currency(
                                reservation.getCurrency()
                        )
                        .status(
                                BillingAllocationStatus.ACTIVE
                        )
                        .allocationDate(
                                Instant.now()
                        )
                        .originalAllocation(null)
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                reservation
                                        .getTransactionGroupId()
                        )
                        .notes(
                                "Allocation created from advance-payment reservation."
                        )
                        .build();

        return saveAllocation(
                allocation,
                idempotencyKey
        );
    }

    private BillingAllocation createAvailableWalletAllocation(
            BillingWallet wallet,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            PatientServiceAndProduct item,
            BigDecimal amount,
            String idempotencyKey,
            UUID transactionGroupId
    ) {
        BillingAllocation allocation =
                BillingAllocation.builder()
                        .allocationNumber(
                                generateAllocationNumber()
                        )
                        .charge(charge)
                        .chargeLine(chargeLine)
                        .chargeResponsibility(
                                responsibility
                        )
                        .patientServiceProduct(item)
                        .patient(
                                responsibility.getPatient()
                        )
                        .encounter(
                                responsibility.getEncounter()
                        )
                        .allocationSourceType(
                                AllocationSourceType.WALLET_AVAILABLE
                        )
                        .reservation(null)
                        .payment(null)
                        .paymentTransaction(null)
                        .debitTransactionId(null)
                        .sourceReferenceType(
                                "BILLING_WALLET"
                        )
                        .sourceReferenceId(
                                wallet.getId()
                        )
                        .sourceReferenceNumber(null)
                        .allocatedAmount(amount)
                        .remainingAllocatedAmount(amount)
                        .reversedAmount(zero())
                        .currency(
                                responsibility.getCurrency()
                        )
                        .status(
                                BillingAllocationStatus.ACTIVE
                        )
                        .allocationDate(
                                Instant.now()
                        )
                        .originalAllocation(null)
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                transactionGroupId
                        )
                        .notes(
                                "Allocation created directly from available patient wallet balance."
                        )
                        .build();

        return saveAllocation(
                allocation,
                idempotencyKey
        );
    }

    private BillingAllocation createPaymentAllocation(
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            PatientServiceAndProduct item,
            BigDecimal amount,
            String idempotencyKey,
            UUID transactionGroupId
    ) {
        BillingAllocation allocation =
                BillingAllocation.builder()
                        .allocationNumber(
                                generateAllocationNumber()
                        )
                        .charge(charge)
                        .chargeLine(chargeLine)
                        .chargeResponsibility(
                                responsibility
                        )
                        .patientServiceProduct(item)
                        .patient(
                                responsibility.getPatient()
                        )
                        .encounter(
                                responsibility.getEncounter()
                        )
                        .allocationSourceType(
                                AllocationSourceType.PAYMENT
                        )
                        .reservation(null)
                        .payment(payment)
                        .paymentTransaction(paymentTransaction)
                        .debitTransactionId(null)
                        .sourceReferenceType(
                                "BILLING_PAYMENT"
                        )
                        .sourceReferenceId(
                                payment.getId()
                        )
                        .sourceReferenceNumber(
                                payment.getPaymentNumber()
                        )
                        .allocatedAmount(amount)
                        .remainingAllocatedAmount(amount)
                        .reversedAmount(zero())
                        .currency(
                                responsibility.getCurrency()
                        )
                        .status(
                                BillingAllocationStatus.ACTIVE
                        )
                        .allocationDate(
                                Instant.now()
                        )
                        .originalAllocation(null)
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                transactionGroupId
                        )
                        .notes(
                                "Allocation created from confirmed invoice payment."
                        )
                        .build();

        return saveAllocation(
                allocation,
                idempotencyKey
        );
    }

    private BillingAllocation saveAllocation(
            BillingAllocation allocation,
            String idempotencyKey
    ) {
        try {
            return billingAllocationRepository
                    .saveAndFlush(allocation);

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingAllocation concurrent =
                    billingAllocationRepository
                            .findByIdempotencyKey(
                                    idempotencyKey
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            LOG.error(
                    "[CREATE] Allocation creation failed "
                            + "idempotencyKey={} amount={} sourceType={}",
                    idempotencyKey,
                    allocation.getAllocatedAmount(),
                    allocation.getAllocationSourceType(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to create billing allocation.",
                    ENTITY_NAME,
                    "allocation.create.failed"
            );
        }
    }

    /*
     * ============================================================
     * UPDATE FINANCIAL BALANCES
     * ============================================================
     */

    private void updateReservationAfterAllocation(
            BillingReservation reservation,
            BigDecimal amount
    ) {
        BigDecimal remaining =
                money(
                        reservation
                                .getRemainingReservedAmount()
                ).subtract(amount);

        if (remaining.signum() < 0) {
            throw new BadRequestAlertException(
                    "Reservation remaining amount cannot become negative.",
                    ENTITY_NAME,
                    "reservation.remaining.negative"
            );
        }

        reservation.setRemainingReservedAmount(
                remaining
        );

        reservation.setConsumedAmount(
                money(
                        reservation.getConsumedAmount()
                ).add(amount)
        );

        reservation.setConsumedDate(
                Instant.now()
        );

        if (remaining.signum() == 0) {
            reservation.setStatus(
                    BillingReservationStatus.CONSUMED
            );
        }

        billingReservationRepository.save(
                reservation
        );
    }

    private void updateResponsibilityAfterAllocation(
            BillingChargeResponsibility responsibility,
            BigDecimal amount
    ) {
        if (responsibility == null
                || responsibility.getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted billing responsibility is required.",
                    ENTITY_NAME,
                    "responsibility.required"
            );
        }

        BigDecimal allocationAmount =
                positiveMoney(amount);

        BigDecimal responsibilityAmount =
                money(
                        responsibility.getResponsibilityAmount()
                );

        BigDecimal currentAllocated =
                money(
                        responsibility.getAllocatedAmount()
                );

        BigDecimal currentOutstanding =
                money(
                        responsibility.getOutstandingAmount()
                );

        if (currentOutstanding.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Responsibility has no outstanding amount to allocate.",
                    ENTITY_NAME,
                    "responsibility.outstanding.zero"
            );
        }

        if (allocationAmount.compareTo(
                currentOutstanding
        ) > 0) {
            throw new BadRequestAlertException(
                    "Allocation amount exceeds responsibility outstanding amount.",
                    ENTITY_NAME,
                    "responsibility.allocation.exceedsOutstanding"
            );
        }

        BigDecimal allocated =
                currentAllocated.add(
                        allocationAmount
                );

        BigDecimal outstanding =
                currentOutstanding.subtract(
                        allocationAmount
                );

        if (allocated.compareTo(
                responsibilityAmount
        ) > 0) {
            throw new BadRequestAlertException(
                    "Allocated amount exceeds responsibility amount.",
                    ENTITY_NAME,
                    "responsibility.allocated.exceedsTotal"
            );
        }

        if (outstanding.signum() < 0) {
            throw new BadRequestAlertException(
                    "Responsibility outstanding amount cannot become negative.",
                    ENTITY_NAME,
                    "responsibility.outstanding.negative"
            );
        }

        BigDecimal expectedOutstanding =
                responsibilityAmount.subtract(
                        allocated
                );

        if (expectedOutstanding.compareTo(
                outstanding
        ) != 0) {
            throw new BadRequestAlertException(
                    "Responsibility financial balance is inconsistent.",
                    ENTITY_NAME,
                    "responsibility.balance.invalid"
            );
        }

        responsibility.setAllocatedAmount(
                allocated
        );

        responsibility.setOutstandingAmount(
                outstanding
        );

        if (outstanding.signum() == 0) {
            responsibility.setStatus(
                    BillingResponsibilityStatus.FULLY_ALLOCATED
            );

            responsibility.setClosedDate(
                    Instant.now()
            );

        } else if (allocated.signum() > 0) {
            responsibility.setStatus(
                    BillingResponsibilityStatus.PARTIALLY_ALLOCATED
            );

            responsibility.setClosedDate(null);

        } else {
            responsibility.setStatus(
                    BillingResponsibilityStatus.CALCULATED
            );

            responsibility.setClosedDate(null);
        }

        billingChargeResponsibilityRepository.save(
                responsibility
        );
    }
    private void updateChargeLineAfterReservationAllocation(
            BillingChargeLine chargeLine,
            BigDecimal amount
    ) {
        BigDecimal allocated =
                money(
                        chargeLine.getAllocatedAmount()
                ).add(amount);

        BigDecimal outstanding =
                money(
                        chargeLine.getOutstandingAmount()
                ).subtract(amount);

        BigDecimal reserved =
                money(
                        chargeLine.getReservedAmount()
                ).subtract(amount);

        if (outstanding.signum() < 0
                || reserved.signum() < 0) {
            throw new BadRequestAlertException(
                    "Charge-line balances cannot become negative.",
                    ENTITY_NAME,
                    "chargeLine.balance.negative"
            );
        }

        chargeLine.setAllocatedAmount(
                allocated
        );

        chargeLine.setOutstandingAmount(
                outstanding
        );

        chargeLine.setReservedAmount(
                reserved
        );

        billingChargeLineRepository.save(
                chargeLine
        );
    }

    private void updateChargeLineAfterAvailableAllocation(
            BillingChargeLine chargeLine,
            BigDecimal amount
    ) {
        BigDecimal allocated =
                money(
                        chargeLine.getAllocatedAmount()
                ).add(amount);

        BigDecimal outstanding =
                money(
                        chargeLine.getOutstandingAmount()
                ).subtract(amount);

        if (outstanding.signum() < 0) {
            throw new BadRequestAlertException(
                    "Charge-line outstanding amount cannot become negative.",
                    ENTITY_NAME,
                    "chargeLine.outstanding.negative"
            );
        }

        chargeLine.setAllocatedAmount(
                allocated
        );

        chargeLine.setOutstandingAmount(
                outstanding
        );

        /*
         * Do not change reservedAmount.
         */
        billingChargeLineRepository.save(
                chargeLine
        );
    }

    private void updateChargeAfterAllocation(
            BillingCharge charge,
            BigDecimal amount
    ) {
        BigDecimal allocated =
                money(
                        charge.getAllocatedAmount()
                ).add(amount);

        BigDecimal outstanding =
                money(
                        charge.getOutstandingAmount()
                ).subtract(amount);

        if (outstanding.signum() < 0) {
            throw new BadRequestAlertException(
                    "Charge outstanding amount cannot become negative.",
                    ENTITY_NAME,
                    "charge.outstanding.negative"
            );
        }

        if (allocated.compareTo(
                money(charge.getNetAmount())
        ) > 0) {
            throw new BadRequestAlertException(
                    "Charge allocated amount exceeds net amount.",
                    ENTITY_NAME,
                    "charge.allocated.exceedsNet"
            );
        }

        charge.setAllocatedAmount(
                allocated
        );

        charge.setOutstandingAmount(
                outstanding
        );

        billingChargeRepository.save(charge);
    }

    /*
     * ============================================================
     * PSP PAYMENT CACHE
     * ============================================================
     */

    private void updatePatientItem(
            PatientServiceAndProduct item,
            BillingChargeResponsibility responsibility
    ) {
        BigDecimal patientPaid =
                money(
                        responsibility.getAllocatedAmount()
                );

        BigDecimal patientRemaining =
                money(
                        responsibility.getOutstandingAmount()
                );

        item.setPaidAmount(
                patientPaid
        );

        item.setRemainingAmount(
                patientRemaining
        );

        if (patientRemaining.signum() == 0) {
            item.setPaymentStatus(
                    PaymentStatus.PAID
            );

        } else if (patientPaid.signum() > 0) {
            item.setPaymentStatus(
                    PaymentStatus.PARTIALLY_PAID
            );

        } else {
            item.setPaymentStatus(
                    PaymentStatus.PENDING
            );
        }

        patientServiceAndProductRepository.save(
                item
        );
    }

    private void updatePatientItemAfterReversal(
            PatientServiceAndProduct item,
            BillingChargeResponsibility responsibility
    ) {
        BigDecimal patientPaid =
                money(
                        responsibility.getAllocatedAmount()
                );

        BigDecimal patientRemaining =
                money(
                        responsibility.getOutstandingAmount()
                );

        item.setPaidAmount(
                patientPaid
        );

        item.setRemainingAmount(
                patientRemaining
        );

        if (patientPaid.signum() == 0) {
            item.setPaymentStatus(
                    PaymentStatus.PENDING
            );

        } else if (patientRemaining.signum() == 0) {
            item.setPaymentStatus(
                    PaymentStatus.PAID
            );

        } else {
            item.setPaymentStatus(
                    PaymentStatus.PARTIALLY_PAID
            );
        }

        patientServiceAndProductRepository.save(
                item
        );
    }

    /*
     * ============================================================
     * REVERSAL BALANCE UPDATES
     * ============================================================
     */

    private void reverseResponsibility(
            BillingChargeResponsibility responsibility,
            BigDecimal amount
    ) {
        BigDecimal allocated =
                money(
                        responsibility.getAllocatedAmount()
                ).subtract(amount);

        BigDecimal outstanding =
                money(
                        responsibility.getOutstandingAmount()
                ).add(amount);

        if (allocated.signum() < 0) {
            throw new BadRequestAlertException(
                    "Responsibility allocated amount cannot become negative.",
                    ENTITY_NAME,
                    "responsibility.allocated.negative"
            );
        }

        if (outstanding.compareTo(
                money(
                        responsibility
                                .getResponsibilityAmount()
                )
        ) > 0) {
            throw new BadRequestAlertException(
                    "Responsibility outstanding amount exceeds responsibility amount.",
                    ENTITY_NAME,
                    "responsibility.outstanding.exceedsTotal"
            );
        }

        responsibility.setAllocatedAmount(
                allocated
        );

        responsibility.setOutstandingAmount(
                outstanding
        );

        responsibility.setClosedDate(null);

        billingChargeResponsibilityRepository
                .save(responsibility);
    }

    private void reverseChargeLine(
            BillingChargeLine chargeLine,
            BigDecimal amount
    ) {
        BigDecimal allocated =
                money(
                        chargeLine.getAllocatedAmount()
                ).subtract(amount);

        BigDecimal outstanding =
                money(
                        chargeLine.getOutstandingAmount()
                ).add(amount);

        if (allocated.signum() < 0) {
            throw new BadRequestAlertException(
                    "Charge-line allocated amount cannot become negative.",
                    ENTITY_NAME,
                    "chargeLine.allocated.negative"
            );
        }

        if (outstanding.compareTo(
                money(chargeLine.getNetAmount())
        ) > 0) {
            throw new BadRequestAlertException(
                    "Charge-line outstanding amount exceeds net amount.",
                    ENTITY_NAME,
                    "chargeLine.outstanding.exceedsNet"
            );
        }

        chargeLine.setAllocatedAmount(
                allocated
        );

        chargeLine.setOutstandingAmount(
                outstanding
        );

        billingChargeLineRepository.save(
                chargeLine
        );
    }

    private void reverseCharge(
            BillingCharge charge,
            BigDecimal amount
    ) {
        BigDecimal allocated =
                money(
                        charge.getAllocatedAmount()
                ).subtract(amount);

        BigDecimal outstanding =
                money(
                        charge.getOutstandingAmount()
                ).add(amount);

        if (allocated.signum() < 0) {
            throw new BadRequestAlertException(
                    "Charge allocated amount cannot become negative.",
                    ENTITY_NAME,
                    "charge.allocated.negative"
            );
        }

        if (outstanding.compareTo(
                money(charge.getNetAmount())
        ) > 0) {
            throw new BadRequestAlertException(
                    "Charge outstanding amount exceeds net amount.",
                    ENTITY_NAME,
                    "charge.outstanding.exceedsNet"
            );
        }

        charge.setAllocatedAmount(
                allocated
        );

        charge.setOutstandingAmount(
                outstanding
        );

        billingChargeRepository.save(charge);
    }

    private void reverseAllocationAmounts(
            BillingAllocation allocation,
            BigDecimal reversalAmount,
            String reason,
            String reversedBy
    ) {
        BigDecimal remaining =
                money(
                        allocation
                                .getRemainingAllocatedAmount()
                ).subtract(reversalAmount);

        BigDecimal reversed =
                money(
                        allocation.getReversedAmount()
                ).add(reversalAmount);

        if (remaining.signum() < 0) {
            throw new BadRequestAlertException(
                    "Allocation remaining amount cannot become negative.",
                    ENTITY_NAME,
                    "allocation.remaining.negative"
            );
        }

        allocation.setRemainingAllocatedAmount(
                remaining
        );

        allocation.setReversedAmount(
                reversed
        );

        allocation.setReversedDate(
                Instant.now()
        );

        allocation.setReversedBy(
                reversedBy.trim()
        );

        allocation.setReversalReason(
                reason.trim()
        );

        if (remaining.signum() == 0) {
            allocation.setStatus(
                    BillingAllocationStatus.REVERSED
            );
        } else {
            allocation.setStatus(
                    BillingAllocationStatus.PARTIALLY_REVERSED
            );
        }

        billingAllocationRepository.save(
                allocation
        );
    }

    /*
     * ============================================================
     * LEDGER
     * ============================================================
     */

    private void recordAllocationLedger(
            BillingAllocation allocation,
            BillingWallet updatedWallet,
            BillingReservation reservation,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            BigDecimal amount,
            BigDecimal walletAvailableBefore,
            BigDecimal walletReservedBefore,
            BigDecimal responsibilityOutstandingBefore,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        allocation.getTransactionGroupId(),
                        requestId.trim(),
                        allocation.getIdempotencyKey()
                                + ":LEDGER",

                        allocation.getPatient(),
                        allocation.getEncounter(),

                        updatedWallet,
                        allocation.getPayment(),
                        allocation.getPaymentTransaction(),

                        charge,
                        chargeLine,
                        responsibility,

                        reservation,
                        allocation,

                        null,
                        null,
                        null,

                        BillingLedgerTransactionType
                                .ALLOCATION_CREATED,

                        BillingLedgerScope.ALLOCATION,

                        amount,
                        allocation.getCurrency(),

                        zero(),
                        amount.negate(),
                        amount,
                        zero(),
                        zero(),
                        amount,
                        amount.negate(),

                        walletAvailableBefore,
                        money(
                                updatedWallet
                                        .getAvailableBalance()
                        ),

                        walletReservedBefore,
                        money(
                                updatedWallet
                                        .getReservedBalance()
                        ),

                        null,
                        null,

                        responsibilityOutstandingBefore,
                        money(
                                responsibility
                                        .getOutstandingAmount()
                        ),

                        BillingLedgerEntryDirection.DEBIT,
                        BillingLedgerEntryCategory.BUSINESS,

                        null,

                        "BILLING_ALLOCATION",
                        allocation.getId(),
                        allocation.getAllocationNumber(),

                        "Advance reservation consumed and allocated to patient responsibility.",

                        null,

                        sourceChannel
                )
        );
    }

    private void recordAvailableWalletAllocationLedger(
            BillingAllocation allocation,
            BillingWallet updatedWallet,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            BigDecimal amount,
            BigDecimal walletAvailableBefore,
            BigDecimal walletReservedBefore,
            BigDecimal responsibilityOutstandingBefore,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        allocation.getTransactionGroupId(),
                        requestId.trim(),
                        allocation.getIdempotencyKey()
                                + ":LEDGER",

                        allocation.getPatient(),
                        allocation.getEncounter(),

                        updatedWallet,
                        null,
                        null,

                        charge,
                        chargeLine,
                        responsibility,

                        null,
                        allocation,

                        null,
                        null,
                        null,

                        BillingLedgerTransactionType
                                .ALLOCATION_CREATED,

                        BillingLedgerScope.ALLOCATION,

                        amount,
                        allocation.getCurrency(),

                        amount.negate(),
                        zero(),
                        amount,
                        zero(),
                        zero(),
                        amount,
                        amount.negate(),

                        walletAvailableBefore,
                        money(
                                updatedWallet
                                        .getAvailableBalance()
                        ),

                        walletReservedBefore,
                        money(
                                updatedWallet
                                        .getReservedBalance()
                        ),

                        null,
                        null,

                        responsibilityOutstandingBefore,
                        money(
                                responsibility
                                        .getOutstandingAmount()
                        ),

                        BillingLedgerEntryDirection.DEBIT,
                        BillingLedgerEntryCategory.BUSINESS,

                        null,

                        "BILLING_WALLET",
                        updatedWallet.getId(),
                        null,

                        "Available wallet balance consumed and allocated to patient responsibility.",

                        null,

                        sourceChannel
                )
        );
    }

    private void recordPaymentAllocationLedger(
            BillingAllocation allocation,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            BigDecimal amount,
            BigDecimal responsibilityOutstandingBefore,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        /*
         * Wallet movement was already recorded when the payment was
         * confirmed (credit/consume/reservation). This entry only
         * reflects responsibility allocation from that payment.
         */
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        allocation.getTransactionGroupId(),
                        requestId.trim(),
                        buildPaymentAllocationLedgerIdempotencyKey(
                                allocation.getId()
                        ),

                        allocation.getPatient(),
                        allocation.getEncounter(),

                        null,
                        allocation.getPayment(),
                        allocation.getPaymentTransaction(),

                        charge,
                        chargeLine,
                        responsibility,

                        null,
                        allocation,

                        null,
                        null,
                        null,

                        BillingLedgerTransactionType
                                .ALLOCATION_CREATED,

                        BillingLedgerScope.ALLOCATION,

                        amount,
                        allocation.getCurrency(),

                        zero(),
                        zero(),
                        zero(),
                        zero(),
                        zero(),
                        amount,
                        amount.negate(),

                        null,
                        null,
                        null,
                        null,

                        null,
                        null,

                        responsibilityOutstandingBefore,
                        money(
                                responsibility
                                        .getOutstandingAmount()
                        ),

                        BillingLedgerEntryDirection.DEBIT,
                        BillingLedgerEntryCategory.BUSINESS,

                        null,

                        "BILLING_PAYMENT",
                        allocation.getPayment().getId(),
                        allocation.getPayment().getPaymentNumber(),

                        "Confirmed payment allocated to patient responsibility.",

                        null,

                        sourceChannel
                )
        );
    }

    private String buildPaymentAllocationIdempotencyKey(
            Long paymentId,
            Long chargeResponsibilityId,
            String requestId
    ) {
        String suffix =
                requestId == null
                        ? ""
                        : requestId.trim();

        if (suffix.length() > 40) {
            suffix =
                    suffix.substring(
                            suffix.length() - 40
                    );
        }

        return truncateIdempotencyKey(
                "ALLOC:PAY:"
                        + paymentId
                        + ":RESP:"
                        + chargeResponsibilityId
                        + ":"
                        + suffix
        );
    }

    private String buildPaymentAllocationLedgerIdempotencyKey(
            Long allocationId
    ) {
        return truncateIdempotencyKey(
                "LEDGER:ALLOC:PAY:"
                        + allocationId
        );
    }

    private String truncateIdempotencyKey(
            String value
    ) {
        if (value == null) {
            return "";
        }

        if (value.length() <= 150) {
            return value;
        }

        return value.substring(
                0,
                150
        );
    }

    private void recordReversalLedger(
            BillingAllocation allocation,
            BillingWallet updatedWallet,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            BigDecimal reversalAmount,
            BigDecimal walletAvailableBefore,
            BigDecimal walletReservedBefore,
            BigDecimal responsibilityOutstandingBefore,
            String reversalIdempotencyKey,
            String requestId,
            String reason,
            BillingLedgerSourceChannel sourceChannel,
            BillingLedger originalLedger
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        allocation.getTransactionGroupId(),
                        requestId.trim(),
                        reversalIdempotencyKey
                                + ":LEDGER",

                        allocation.getPatient(),
                        allocation.getEncounter(),

                        updatedWallet,
                        allocation.getPayment(),
                        allocation.getPaymentTransaction(),

                        charge,
                        chargeLine,
                        responsibility,

                        allocation.getReservation(),
                        allocation,

                        null,
                        null,
                        null,

                        BillingLedgerTransactionType
                                .ALLOCATION_REVERSED,

                        BillingLedgerScope.ALLOCATION,

                        reversalAmount,
                        allocation.getCurrency(),

                        reversalAmount,
                        zero(),
                        reversalAmount.negate(),
                        zero(),
                        zero(),
                        reversalAmount.negate(),
                        reversalAmount,

                        walletAvailableBefore,
                        money(
                                updatedWallet
                                        .getAvailableBalance()
                        ),

                        walletReservedBefore,
                        money(
                                updatedWallet
                                        .getReservedBalance()
                        ),

                        null,
                        null,

                        responsibilityOutstandingBefore,
                        money(
                                responsibility
                                        .getOutstandingAmount()
                        ),

                        BillingLedgerEntryDirection.CREDIT,
                        BillingLedgerEntryCategory.REVERSAL,

                        originalLedger,

                        "BILLING_ALLOCATION",
                        allocation.getId(),
                        allocation.getAllocationNumber(),

                        "Allocation reversed and consumed amount returned to available wallet balance.",

                        reason.trim(),

                        sourceChannel
                )
        );
    }

    /*
     * ============================================================
     * AMOUNT RESOLUTION
     * ============================================================
     */

    private BigDecimal resolveReservationAllocationAmount(
            BigDecimal requestedAmount,
            BillingReservation reservation,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingWallet wallet
    ) {
        BigDecimal requested =
                positiveMoney(
                        requestedAmount
                );

        BigDecimal reservationRemaining =
                money(
                        reservation
                                .getRemainingReservedAmount()
                );

        BigDecimal responsibilityOutstanding =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal lineOutstanding =
                money(
                        chargeLine.getOutstandingAmount()
                );

        BigDecimal walletReserved =
                money(
                        wallet.getReservedBalance()
                );

        BigDecimal amount =
                minimum(
                        requested,
                        reservationRemaining,
                        responsibilityOutstanding,
                        lineOutstanding,
                        walletReserved
                );

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "No amount is available for reservation allocation.",
                    ENTITY_NAME,
                    "allocation.amount.zero"
            );
        }

        return amount;
    }

    private BigDecimal resolveAvailableWalletAllocationAmount(
            BigDecimal requestedAmount,
            BillingWallet wallet,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine
    ) {
        BigDecimal requested =
                positiveMoney(
                        requestedAmount
                );

        BigDecimal available =
                money(
                        wallet.getAvailableBalance()
                );

        BigDecimal responsibilityOutstanding =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal lineOutstanding =
                money(
                        chargeLine.getOutstandingAmount()
                );

        BigDecimal amount =
                minimum(
                        requested,
                        available,
                        responsibilityOutstanding,
                        lineOutstanding
                );

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "No available wallet amount can be allocated.",
                    ENTITY_NAME,
                    "availableWalletAllocation.amount.zero"
            );
        }

        return amount;
    }

    private BigDecimal resolveReversalAmount(
            BillingAllocation allocation,
            BigDecimal requestedAmount
    ) {
        BigDecimal requested =
                positiveMoney(
                        requestedAmount
                );

        BigDecimal remainingAllocated =
                money(
                        allocation
                                .getRemainingAllocatedAmount()
                );

        if (remainingAllocated.signum() == 0) {
            throw new BadRequestAlertException(
                    "Allocation has no remaining amount to reverse.",
                    ENTITY_NAME,
                    "allocation.remaining.zero"
            );
        }

        if (requested.compareTo(
                remainingAllocated
        ) > 0) {
            throw new BadRequestAlertException(
                    "Reversal amount exceeds remaining allocated amount.",
                    ENTITY_NAME,
                    "reversal.exceedsRemaining"
            );
        }

        return requested;
    }

    /*
     * ============================================================
     * LOADERS
     * ============================================================
     */

    private BillingChargeResponsibility lockResponsibility(
            Long responsibilityId
    ) {
        return billingChargeResponsibilityRepository
                .findById(responsibilityId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Billing responsibility not found with id "
                                        + responsibilityId,
                                ENTITY_NAME,
                                "responsibility.notfound"
                        )
                );
    }

    private BillingChargeLine lockChargeLine(
            Long chargeLineId
    ) {
        return billingChargeLineRepository
                .findById(chargeLineId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Billing charge line not found with id "
                                        + chargeLineId,
                                ENTITY_NAME,
                                "chargeLine.notfound"
                        )
                );
    }

    private BillingCharge lockCharge(
            Long chargeId
    ) {
        return billingChargeRepository
                .findById(chargeId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Billing charge not found with id "
                                        + chargeId,
                                ENTITY_NAME,
                                "charge.notfound"
                        )
                );
    }

    private PatientServiceAndProduct
    loadPatientServiceProduct(
            Long patientServiceProductId
    ) {
        return patientServiceAndProductRepository
                .findById(patientServiceProductId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient service/product not found with id "
                                        + patientServiceProductId,
                                ENTITY_NAME,
                                "patientServiceProduct.notfound"
                        )
                );
    }

    /*
     * ============================================================
     * RESULTS
     * ============================================================
     */

    private BillingAllocationResult buildResult(
            BillingAllocation allocation,
            BillingWallet wallet
    ) {
        return new BillingAllocationResult(
                allocation.getId(),
                allocation.getAllocationNumber(),

                allocation.getReservation() == null
                        ? null
                        : allocation
                        .getReservation()
                        .getId(),

                allocation.getCharge().getId(),

                allocation.getChargeLine().getId(),

                allocation
                        .getChargeResponsibility()
                        .getId(),

                allocation
                        .getPatientServiceProduct()
                        .getId(),

                money(
                        allocation.getAllocatedAmount()
                ),

                money(
                        allocation
                                .getChargeResponsibility()
                                .getOutstandingAmount()
                ),

                money(
                        allocation
                                .getChargeLine()
                                .getOutstandingAmount()
                ),

                money(
                        allocation
                                .getCharge()
                                .getOutstandingAmount()
                ),

                wallet == null
                        ? zero()
                        : money(
                        wallet.getAvailableBalance()
                ),

                wallet == null
                        ? zero()
                        : money(
                        wallet.getReservedBalance()
                ),

                wallet == null
                        ? zero()
                        : money(
                        wallet.getConsumedAmount()
                )
        );
    }

    private BillingAllocationReversalResult
    buildReversalResult(
            BillingAllocation allocation,
            BigDecimal reversedNow,
            BillingWallet wallet
    ) {
        return new BillingAllocationReversalResult(
                allocation.getId(),

                allocation.getAllocationNumber(),

                money(reversedNow),

                money(
                        allocation
                                .getRemainingAllocatedAmount()
                ),

                money(
                        allocation
                                .getChargeResponsibility()
                                .getOutstandingAmount()
                ),

                money(
                        allocation
                                .getChargeLine()
                                .getOutstandingAmount()
                ),

                money(
                        allocation
                                .getCharge()
                                .getOutstandingAmount()
                ),

                wallet == null
                        ? zero()
                        : money(
                        wallet.getAvailableBalance()
                ),

                wallet == null
                        ? zero()
                        : money(
                        wallet.getConsumedAmount()
                ),

                allocation.getStatus()
        );
    }

    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */

    private void validatePatientResponsibility(
            BillingChargeResponsibility responsibility
    ) {
        if (responsibility.getResponsiblePartyType()
                != ResponsiblePartyType.PATIENT) {
            throw new BadRequestAlertException(
                    "Patient wallet may only be allocated to patient responsibility.",
                    ENTITY_NAME,
                    "responsibility.notPatient"
            );
        }

        if (money(
                responsibility.getOutstandingAmount()
        ).signum() <= 0) {
            throw new BadRequestAlertException(
                    "Patient responsibility has no outstanding amount.",
                    ENTITY_NAME,
                    "responsibility.outstanding.zero"
            );
        }

        if (responsibility.getPatient() == null
                || responsibility.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Responsibility patient is missing.",
                    ENTITY_NAME,
                    "responsibility.patient.missing"
            );
        }

        if (responsibility.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Responsibility currency is missing.",
                    ENTITY_NAME,
                    "responsibility.currency.missing"
            );
        }
    }

    private void validateReservationAllocationInput(
            Long reservationId,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (reservationId == null) {
            throw new BadRequestAlertException(
                    "Reservation ID is required.",
                    ENTITY_NAME,
                    "reservationId.required"
            );
        }

        positiveMoney(
                requestedAmount
        );

        validateRequestInformation(
                requestId,
                sourceChannel
        );
    }

    private void validateAvailableWalletAllocationInput(
            Long chargeResponsibilityId,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (chargeResponsibilityId == null) {
            throw new BadRequestAlertException(
                    "Charge responsibility ID is required.",
                    ENTITY_NAME,
                    "chargeResponsibilityId.required"
            );
        }

        positiveMoney(
                requestedAmount
        );

        validateRequestInformation(
                requestId,
                sourceChannel
        );
    }

    private void validatePaymentAllocationInput(
            Long chargeResponsibilityId,
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (chargeResponsibilityId == null) {
            throw new BadRequestAlertException(
                    "Charge responsibility ID is required.",
                    ENTITY_NAME,
                    "chargeResponsibilityId.required"
            );
        }

        if (payment == null || payment.getId() == null) {
            throw new BadRequestAlertException(
                    "Payment is required.",
                    ENTITY_NAME,
                    "payment.required"
            );
        }

        if (paymentTransaction != null
                && (paymentTransaction.getPayment() == null
                || !paymentTransaction
                .getPayment()
                .getId()
                .equals(payment.getId()))) {
            throw new BadRequestAlertException(
                    "Payment transaction does not belong to the payment.",
                    ENTITY_NAME,
                    "paymentTransaction.payment.mismatch"
            );
        }

        positiveMoney(
                requestedAmount
        );

        validateRequestInformation(
                requestId,
                sourceChannel
        );
    }

    private BigDecimal resolvePaymentAllocationAmount(
            BigDecimal requestedAmount,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine
    ) {
        BigDecimal requested =
                positiveMoney(
                        requestedAmount
                );

        BigDecimal responsibilityOutstanding =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal lineOutstanding =
                money(
                        chargeLine.getOutstandingAmount()
                );

        BigDecimal amount =
                minimum(
                        requested,
                        responsibilityOutstanding,
                        lineOutstanding
                );

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "No outstanding responsibility balance is available for payment allocation.",
                    ENTITY_NAME,
                    "payment.allocation.zero"
            );
        }

        return amount;
    }

    private void validateReversalInput(
            Long allocationId,
            BigDecimal requestedAmount,
            String reason,
            String reversedBy,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (allocationId == null) {
            throw new BadRequestAlertException(
                    "Allocation ID is required.",
                    ENTITY_NAME,
                    "allocationId.required"
            );
        }

        positiveMoney(
                requestedAmount
        );

        if (reason == null
                || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Allocation reversal reason is required.",
                    ENTITY_NAME,
                    "reason.required"
            );
        }

        if (reversedBy == null
                || reversedBy.isBlank()) {
            throw new BadRequestAlertException(
                    "Reversed-by user is required.",
                    ENTITY_NAME,
                    "reversedBy.required"
            );
        }

        validateRequestInformation(
                requestId,
                sourceChannel
        );
    }

    private void validateRequestInformation(
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (requestId == null
                || requestId.isBlank()) {
            throw new BadRequestAlertException(
                    "Request ID is required.",
                    ENTITY_NAME,
                    "requestId.required"
            );
        }

        if (sourceChannel == null) {
            throw new BadRequestAlertException(
                    "Ledger source channel is required.",
                    ENTITY_NAME,
                    "sourceChannel.required"
            );
        }
    }

    /*
     * ============================================================
     * MONEY HELPERS
     * ============================================================
     */

    private BigDecimal positiveMoney(
            BigDecimal value
    ) {
        BigDecimal amount =
                money(value);

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Amount must be greater than zero.",
                    ENTITY_NAME,
                    "amount.invalid"
            );
        }

        return amount;
    }

    private BigDecimal minimum(
            BigDecimal... values
    ) {
        BigDecimal result = null;

        for (BigDecimal value : values) {
            BigDecimal normalized =
                    money(value);

            if (result == null
                    || normalized.compareTo(result) < 0) {
                result = normalized;
            }
        }

        return result == null
                ? zero()
                : result;
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return value == null
                ? zero()
                : value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private String generateAllocationNumber() {
        return "ALC-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private BillingAllocation createDebitAllocation(
            BillingDebitTransaction debitTransaction,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            PatientServiceAndProduct item,
            BigDecimal amount,
            String idempotencyKey
    ) {
        BillingAllocation allocation =
                BillingAllocation.builder()
                        .allocationNumber(
                                generateAllocationNumber()
                        )

                        .charge(charge)

                        .chargeLine(chargeLine)

                        .chargeResponsibility(
                                responsibility
                        )

                        .patientServiceProduct(item)

                        .patient(
                                responsibility.getPatient()
                        )

                        .encounter(
                                responsibility.getEncounter()
                        )

                        .allocationSourceType(
                                AllocationSourceType.DEBIT
                        )

                        .reservation(null)

                        .payment(null)

                        .paymentTransaction(null)

                        /*
                         * BillingAllocation currently stores debit
                         * transaction as a scalar ID.
                         */
                        .debitTransactionId(
                                debitTransaction.getId()
                        )

                        .sourceReferenceType(
                                "BILLING_DEBIT_TRANSACTION"
                        )

                        .sourceReferenceId(
                                debitTransaction.getId()
                        )

                        .sourceReferenceNumber(
                                debitTransaction
                                        .getTransactionNumber()
                        )

                        .allocatedAmount(amount)

                        .remainingAllocatedAmount(
                                amount
                        )

                        .reversedAmount(
                                zero()
                        )

                        .currency(
                                responsibility.getCurrency()
                        )

                        .status(
                                BillingAllocationStatus.ACTIVE
                        )

                        .allocationDate(
                                Instant.now()
                        )

                        .originalAllocation(null)

                        .idempotencyKey(
                                idempotencyKey
                        )

                        .transactionGroupId(
                                debitTransaction
                                        .getTransactionGroupId()
                        )

                        .notes(
                                "Allocation created from patient debit transaction."
                        )

                        .build();

        return saveAllocation(
                allocation,
                idempotencyKey
        );
    }

    private BigDecimal resolveDebitAllocationAmount(
            BillingDebitTransaction debitTransaction,
            BigDecimal requestedAmount,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine
    ) {
        BigDecimal requested =
                positiveMoney(
                        requestedAmount
                );

        BigDecimal debitTransactionAmount =
                money(
                        debitTransaction.getAmount()
                );

        BigDecimal alreadyAllocated =
                billingAllocationRepository
                        .findAllByDebitTransactionIdOrderByAllocationDateAscIdAsc(
                                debitTransaction.getId()
                        )
                        .stream()
                        .map(BillingAllocation::getAllocatedAmount)
                        .map(this::money)
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        /*
         * Since an allocation reversal returns the charge amount
         * to outstanding but does not automatically reverse the debit,
         * the original debit source remains used.
         */
        BigDecimal debitRemaining =
                debitTransactionAmount
                        .subtract(
                                alreadyAllocated
                        )
                        .max(zero());

        BigDecimal responsibilityOutstanding =
                money(
                        responsibility.getOutstandingAmount()
                );

        BigDecimal chargeLineOutstanding =
                money(
                        chargeLine.getOutstandingAmount()
                );

        BigDecimal amount =
                minimum(
                        requested,
                        debitRemaining,
                        responsibilityOutstanding,
                        chargeLineOutstanding
                );

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "No debit amount is available for allocation.",
                    ENTITY_NAME,
                    "debitAllocation.amount.zero"
            );
        }

        return amount;
    }

    private void validateDebitTransaction(
            BillingDebitTransaction debitTransaction
    ) {
        if (debitTransaction.getStatus()
                != BillingDebitTransactionStatus.COMPLETED) {

            throw new BadRequestAlertException(
                    "Only completed debit transactions may be allocated.",
                    ENTITY_NAME,
                    "debitTransaction.notCompleted"
            );
        }

        if (debitTransaction.getTransactionType()
                != BillingDebitTransactionType.DEBIT_CREATED) {

            throw new BadRequestAlertException(
                    "Only DEBIT_CREATED transactions may fund charge allocations.",
                    ENTITY_NAME,
                    "debitTransaction.type.invalid"
            );
        }

        if (money(
                debitTransaction.getAmount()
        ).signum() <= 0) {

            throw new BadRequestAlertException(
                    "Debit transaction has no valid amount.",
                    ENTITY_NAME,
                    "debitTransaction.amount.invalid"
            );
        }

        if (debitTransaction.getDebitAccount()
                == null
                || debitTransaction
                .getDebitAccount()
                .getId() == null) {

            throw new BadRequestAlertException(
                    "Debit transaction does not have a debit account.",
                    ENTITY_NAME,
                    "debitTransaction.account.missing"
            );
        }

        if (debitTransaction.getPatient()
                == null
                || debitTransaction
                .getPatient()
                .getId() == null) {

            throw new BadRequestAlertException(
                    "Debit transaction does not have a patient.",
                    ENTITY_NAME,
                    "debitTransaction.patient.missing"
            );
        }

        if (debitTransaction.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Debit transaction currency is missing.",
                    ENTITY_NAME,
                    "debitTransaction.currency.missing"
            );
        }
    }

    private void validateDebitTransactionRelations(
            BillingDebitTransaction debitTransaction,
            BillingChargeResponsibility responsibility,
            BillingChargeLine chargeLine,
            BillingCharge charge,
            PatientServiceAndProduct item
    ) {
        if (!debitTransaction
                .getPatient()
                .getId()
                .equals(
                        responsibility
                                .getPatient()
                                .getId()
                )) {

            throw new BadRequestAlertException(
                    "Debit transaction patient does not match responsibility patient.",
                    ENTITY_NAME,
                    "debitTransaction.patient.mismatch"
            );
        }

        if (debitTransaction.getCurrency()
                != responsibility.getCurrency()) {

            throw new BadRequestAlertException(
                    "Debit transaction currency does not match responsibility currency.",
                    ENTITY_NAME,
                    "debitTransaction.currency.mismatch"
            );
        }

        if (debitTransaction.getCharge() != null
                && !debitTransaction
                .getCharge()
                .getId()
                .equals(charge.getId())) {

            throw new BadRequestAlertException(
                    "Debit transaction charge does not match allocation charge.",
                    ENTITY_NAME,
                    "debitTransaction.charge.mismatch"
            );
        }

        if (debitTransaction.getChargeLine() != null
                && !debitTransaction
                .getChargeLine()
                .getId()
                .equals(chargeLine.getId())) {

            throw new BadRequestAlertException(
                    "Debit transaction charge line does not match allocation charge line.",
                    ENTITY_NAME,
                    "debitTransaction.chargeLine.mismatch"
            );
        }

        if (debitTransaction
                .getPatientServiceProduct() != null
                && !debitTransaction
                .getPatientServiceProduct()
                .getId()
                .equals(item.getId())) {

            throw new BadRequestAlertException(
                    "Debit transaction service/product does not match allocation item.",
                    ENTITY_NAME,
                    "debitTransaction.patientServiceProduct.mismatch"
            );
        }
    }

    private void validateDebitAllocationInput(
            Long debitTransactionId,
            BigDecimal requestedAmount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (debitTransactionId == null) {
            throw new BadRequestAlertException(
                    "Debit transaction ID is required.",
                    ENTITY_NAME,
                    "debitTransactionId.required"
            );
        }

        positiveMoney(
                requestedAmount
        );

        validateRequestInformation(
                requestId,
                sourceChannel
        );
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingAllocationReversalResult reverseDebitAllocationBalances(
            Long allocationId,
            BigDecimal requestedAmount,
            String reason,
            String reversedBy
    ) {
        if (allocationId == null) {
            throw new BadRequestAlertException(
                    "Allocation ID is required.",
                    ENTITY_NAME,
                    "allocationId.required"
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Debit-allocation reversal reason is required.",
                    ENTITY_NAME,
                    "reason.required"
            );
        }

        if (reversedBy == null || reversedBy.isBlank()) {
            throw new BadRequestAlertException(
                    "Reversed-by user is required.",
                    ENTITY_NAME,
                    "reversedBy.required"
            );
        }

        BillingAllocation allocation =
                billingAllocationRepository
                        .findById(allocationId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing allocation not found with id "
                                                + allocationId,
                                        ENTITY_NAME,
                                        "allocation.notfound"
                                )
                        );

        if (allocation.getAllocationSourceType()
                != AllocationSourceType.DEBIT) {
            throw new BadRequestAlertException(
                    "Only debit allocations may be reversed through this operation.",
                    ENTITY_NAME,
                    "allocation.notDebit"
            );
        }

        if (allocation.getStatus()
                == BillingAllocationStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled allocation cannot be reversed.",
                    ENTITY_NAME,
                    "allocation.cancelled"
            );
        }

        if (allocation.getStatus()
                == BillingAllocationStatus.REVERSED) {
            return buildReversalResult(
                    allocation,
                    zero(),
                    null
            );
        }

        BigDecimal reversalAmount =
                resolveReversalAmount(
                        allocation,
                        requestedAmount
                );

        BillingChargeResponsibility responsibility =
                lockResponsibility(
                        allocation
                                .getChargeResponsibility()
                                .getId()
                );

        BillingChargeLine chargeLine =
                lockChargeLine(
                        allocation
                                .getChargeLine()
                                .getId()
                );

        BillingCharge charge =
                lockCharge(
                        allocation
                                .getCharge()
                                .getId()
                );

        PatientServiceAndProduct item =
                loadPatientServiceProduct(
                        allocation
                                .getPatientServiceProduct()
                                .getId()
                );

        reverseResponsibility(
                responsibility,
                reversalAmount
        );

        reverseChargeLine(
                chargeLine,
                reversalAmount
        );

        reverseCharge(
                charge,
                reversalAmount
        );

        reverseAllocationAmounts(
                allocation,
                reversalAmount,
                reason,
                reversedBy
        );

        updatePatientItemAfterReversal(
                item,
                responsibility
        );

        LOG.info(
                "[REVERSE_DEBIT_ALLOCATION] Debit allocation balances reversed "
                        + "allocationId={} amount={} "
                        + "responsibilityOutstanding={} "
                        + "chargeLineOutstanding={} "
                        + "chargeOutstanding={}",
                allocation.getId(),
                reversalAmount,
                responsibility.getOutstandingAmount(),
                chargeLine.getOutstandingAmount(),
                charge.getOutstandingAmount()
        );

        return buildReversalResult(
                allocation,
                reversalAmount,
                null
        );
    }
}
