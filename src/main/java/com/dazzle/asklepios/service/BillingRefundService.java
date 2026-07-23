package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.BillingRefund;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryCategory;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryDirection;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerScope;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerTransactionType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundStatus;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.BillingPaymentTransactionRepository;
import com.dazzle.asklepios.repository.BillingRefundRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.billing.BillingLedgerEntryRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundResult;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingRefundService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingRefundService.class
            );

    private static final String ENTITY_NAME =
            "billingRefund";

    private static final int MONEY_SCALE = 4;

    private static final EnumSet<BillingRefundStatus>
            FINANCIALLY_COMPLETED_REFUND_STATUSES =
            EnumSet.of(
                    BillingRefundStatus.COMPLETED,
                    BillingRefundStatus.PARTIALLY_COMPLETED
            );

    private final BillingRefundRepository
            billingRefundRepository;

    private final BillingPaymentRepository
            billingPaymentRepository;

    private final BillingPaymentTransactionRepository
            billingPaymentTransactionRepository;

    private final PatientRepository
            patientRepository;

    private final PatientEncounterRepository
            patientEncounterRepository;

    private final BillingWalletService
            billingWalletService;

    private final BillingLedgerService
            billingLedgerService;

    @Transactional(rollbackFor = Exception.class)
    public BillingRefundResult refundAvailableBalance(
            BillingRefundRequest request
    ) {
        validateRefundRequest(request);

        String idempotencyKey =
                "REFUND:"
                        + request.requestId().trim();

        BillingRefund existing =
                billingRefundRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            return buildResult(existing);
        }

        Patient patient =
                patientRepository
                        .findById(request.patientId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient not found with id "
                                                + request.patientId(),
                                        ENTITY_NAME,
                                        "patient.notfound"
                                )
                        );

        PatientEncounter encounter =
                loadEncounter(
                        request.encounterId(),
                        patient
                );

        BillingPayment originalPayment =
                loadOriginalPayment(
                        request,
                        patient
                );

        BillingPaymentTransaction
                originalPaymentTransaction =
                loadOriginalPaymentTransaction(
                        request,
                        originalPayment
                );

        BillingWallet wallet =
                resolveAndLockWallet(
                        request,
                        originalPayment
                );

        validateWalletPatient(
                wallet,
                patient
        );

        BigDecimal availableBefore =
                money(
                        wallet.getAvailableBalance()
                );

        BigDecimal refundedBefore =
                money(
                        wallet.getRefundedAmount()
                );

        BigDecimal refundableAmount =
                resolveRefundableAmount(
                        request,
                        wallet,
                        originalPayment
                );

        BillingRefund refund =
                createRequestedRefund(
                        request,
                        wallet,
                        patient,
                        encounter,
                        originalPayment,
                        originalPaymentTransaction,
                        refundableAmount,
                        idempotencyKey
                );

        refund.setStatus(
                BillingRefundStatus.PROCESSING
        );

        billingRefundRepository.save(refund);

        BillingWallet updatedWallet =
                billingWalletService
                        .refundAvailable(
                                wallet,
                                refundableAmount
                        );

        BillingPaymentTransaction
                refundPaymentTransaction =
                createRefundPaymentTransaction(
                        request,
                        refund,
                        originalPayment,
                        originalPaymentTransaction,
                        refundableAmount
                );

        completeRefund(
                refund,
                refundPaymentTransaction,
                refundableAmount,
                request.requestedBy()
        );

        updateOriginalPaymentStatus(
                originalPayment
        );

        recordRefundLedger(
                refund,
                updatedWallet,
                refundPaymentTransaction,
                availableBefore,
                refundedBefore,
                request
        );

        LOG.info(
                "[COMPLETE_REFUND] Refund completed "
                        + "refundId={} refundNumber={} "
                        + "patientId={} amount={} "
                        + "availableBefore={} availableAfter={}",
                refund.getId(),
                refund.getRefundNumber(),
                patient.getId(),
                refundableAmount,
                availableBefore,
                updatedWallet.getAvailableBalance()
        );

        return buildResult(refund);
    }

    @Transactional(rollbackFor = Exception.class)
    public BillingRefundReversalResult reverseRefund(
            Long refundId,
            BigDecimal requestedAmount,
            String reason,
            String reversedBy,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateRefundReversalInput(
                refundId,
                requestedAmount,
                reason,
                reversedBy,
                requestId,
                sourceChannel
        );

        BillingRefund originalRefund =
                billingRefundRepository
                        .findById(refundId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing refund not found with id "
                                                + refundId,
                                        ENTITY_NAME,
                                        "refund.notfound"
                                )
                        );

        if (originalRefund.getStatus()
                != BillingRefundStatus.COMPLETED
                && originalRefund.getStatus()
                != BillingRefundStatus
                .PARTIALLY_COMPLETED) {

            throw new BadRequestAlertException(
                    "Only completed refunds may be reversed.",
                    ENTITY_NAME,
                    "refund.notCompleted"
            );
        }

        String idempotencyKey =
                "REFUND_REVERSAL:"
                        + refundId
                        + ":"
                        + requestId.trim();

        BillingRefund existingReversal =
                billingRefundRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existingReversal != null) {
            return buildReversalResult(
                    originalRefund,
                    existingReversal
            );
        }

        BigDecimal reversibleAmount =
                money(
                        originalRefund.getRefundedAmount()
                ).subtract(
                        money(
                                originalRefund.getReversedAmount()
                        )
                );

        BigDecimal reversalAmount =
                positiveMoney(
                        requestedAmount
                );

        if (reversalAmount.compareTo(
                reversibleAmount
        ) > 0) {
            throw new BadRequestAlertException(
                    "Refund reversal amount exceeds the remaining refundable amount.",
                    ENTITY_NAME,
                    "refundReversal.exceedsRemaining"
            );
        }

        BillingWallet wallet =
                billingWalletService.lockWallet(
                        originalRefund
                                .getPatient()
                                .getId()
                );

        if (!wallet.getId()
                .equals(
                        originalRefund
                                .getWallet()
                                .getId()
                )) {
            throw new BadRequestAlertException(
                    "Refund wallet does not match the patient's billing wallet.",
                    ENTITY_NAME,
                    "refund.wallet.mismatch"
            );
        }

        BigDecimal availableBefore =
                money(
                        wallet.getAvailableBalance()
                );

        BigDecimal refundedBefore =
                money(
                        wallet.getRefundedAmount()
                );

        BillingWallet updatedWallet =
                billingWalletService
                        .reverseRefundToAvailable(
                                wallet,
                                reversalAmount
                        );

        BillingRefund reversalRefund =
                BillingRefund.builder()
                        .refundNumber(
                                generateRefundNumber()
                        )
                        .wallet(updatedWallet)
                        .patient(
                                originalRefund.getPatient()
                        )
                        .encounter(
                                originalRefund.getEncounter()
                        )
                        .originalPayment(
                                originalRefund
                                        .getOriginalPayment()
                        )
                        .originalPaymentTransaction(
                                originalRefund
                                        .getOriginalPaymentTransaction()
                        )
                        .refundPaymentTransaction(null)
                        .refundSourceType(
                                originalRefund
                                        .getRefundSourceType()
                        )
                        .requestedAmount(
                                reversalAmount
                        )
                        .approvedAmount(
                                reversalAmount
                        )
                        .refundedAmount(zero())
                        .reversedAmount(
                                reversalAmount
                        )
                        .currency(
                                originalRefund.getCurrency()
                        )
                        .refundMethodCode(
                                originalRefund
                                        .getRefundMethodCode()
                        )
                        .refundMethodId(
                                originalRefund
                                        .getRefundMethodId()
                        )
                        .status(
                                BillingRefundStatus.REVERSED
                        )
                        .requestDate(
                                Instant.now()
                        )
                        .requestedBy(
                                reversedBy.trim()
                        )
                        .reason(
                                reason.trim()
                        )
                        .approvedBy(
                                reversedBy.trim()
                        )
                        .approvedDate(
                                Instant.now()
                        )
                        .completedBy(
                                reversedBy.trim()
                        )
                        .completedDate(
                                Instant.now()
                        )
                        .reversedBy(
                                reversedBy.trim()
                        )
                        .reversedDate(
                                Instant.now()
                        )
                        .reversalReason(
                                reason.trim()
                        )
                        .originalRefund(
                                originalRefund
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                UUID.randomUUID()
                        )
                        .notes(
                                "Reversal of refund "
                                        + originalRefund
                                        .getRefundNumber()
                        )
                        .build();

        reversalRefund =
                saveRefund(
                        reversalRefund,
                        idempotencyKey
                );

        originalRefund.setReversedAmount(
                money(
                        originalRefund.getReversedAmount()
                ).add(reversalAmount)
        );

        if (money(
                originalRefund.getReversedAmount()
        ).compareTo(
                money(
                        originalRefund.getRefundedAmount()
                )
        ) >= 0) {
            originalRefund.setStatus(
                    BillingRefundStatus.REVERSED
            );
        }

        originalRefund.setReversedBy(
                reversedBy.trim()
        );

        originalRefund.setReversedDate(
                Instant.now()
        );

        originalRefund.setReversalReason(
                reason.trim()
        );

        billingRefundRepository.save(
                originalRefund
        );

        recordRefundReversalLedger(
                originalRefund,
                reversalRefund,
                updatedWallet,
                reversalAmount,
                availableBefore,
                refundedBefore,
                requestId,
                reason,
                sourceChannel
        );

        updateOriginalPaymentStatus(
                originalRefund.getOriginalPayment()
        );

        return buildReversalResult(
                originalRefund,
                reversalRefund
        );
    }

    private BillingRefund createRequestedRefund(
            BillingRefundRequest request,
            BillingWallet wallet,
            Patient patient,
            PatientEncounter encounter,
            BillingPayment originalPayment,
            BillingPaymentTransaction originalTransaction,
            BigDecimal approvedAmount,
            String idempotencyKey
    ) {
        BillingRefund refund =
                BillingRefund.builder()
                        .refundNumber(
                                generateRefundNumber()
                        )
                        .wallet(wallet)
                        .patient(patient)
                        .encounter(encounter)
                        .originalPayment(
                                originalPayment
                        )
                        .originalPaymentTransaction(
                                originalTransaction
                        )
                        .refundPaymentTransaction(null)
                        .refundSourceType(
                                request.refundSourceType()
                        )
                        .requestedAmount(
                                money(
                                        request.requestedAmount()
                                )
                        )
                        .approvedAmount(
                                approvedAmount
                        )
                        .refundedAmount(zero())
                        .reversedAmount(zero())
                        .currency(
                                wallet.getCurrency()
                        )
                        .refundMethodCode(
                                request.refundMethodCode()
                                        .trim()
                        )
                        .refundMethodId(
                                request.refundMethodId()
                        )
                        .status(
                                BillingRefundStatus.REQUESTED
                        )
                        .requestDate(
                                Instant.now()
                        )
                        .requestedBy(
                                request.requestedBy()
                                        .trim()
                        )
                        .reason(
                                request.reason().trim()
                        )
                        .approvedBy(
                                request.requestedBy()
                                        .trim()
                        )
                        .approvedDate(
                                Instant.now()
                        )
                        .externalReference(
                                trimToNull(
                                        request.externalReference()
                                )
                        )
                        .processorReference(
                                trimToNull(
                                        request.processorReference()
                                )
                        )
                        .referenceDocumentType(
                                trimToNull(
                                        request.referenceDocumentType()
                                )
                        )
                        .referenceDocumentId(
                                request.referenceDocumentId()
                        )
                        .referenceDocumentNumber(
                                trimToNull(
                                        request
                                                .referenceDocumentNumber()
                                )
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                UUID.randomUUID()
                        )
                        .notes(
                                trimToNull(
                                        request.notes()
                                )
                        )
                        .build();

        return saveRefund(
                refund,
                idempotencyKey
        );
    }

    private BillingPaymentTransaction
    createRefundPaymentTransaction(
            BillingRefundRequest request,
            BillingRefund refund,
            BillingPayment originalPayment,
            BillingPaymentTransaction originalTransaction,
            BigDecimal amount
    ) {
        if (originalPayment == null) {
            return null;
        }

        String transactionIdempotencyKey =
                refund.getIdempotencyKey()
                        + ":PAYMENT_TRANSACTION";

        BillingPaymentTransaction existing =
                billingPaymentTransactionRepository
                        .findByIdempotencyKey(
                                transactionIdempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            refund.setRefundPaymentTransaction(
                    existing
            );
            return existing;
        }

        BillingPaymentTransaction transaction =
                BillingPaymentTransaction.builder()
                        .transactionNumber(
                                generateTransactionNumber()
                        )
                        .payment(
                                originalPayment
                        )
                        .parentTransaction(
                                originalTransaction
                        )
                        .transactionType(
                                BillingPaymentTransactionType.REFUND
                        )
                        .paymentMethodId(
                                request.refundMethodId()
                        )
                        .paymentMethodCode(
                                request.refundMethodCode()
                                        .trim()
                        )
                        .amount(amount)
                        .currency(
                                refund.getCurrency()
                        )
                        .status(
                                BillingPaymentTransactionStatus.SUCCESS
                        )
                        .transactionDate(
                                Instant.now()
                        )
                        .externalReference(
                                trimToNull(
                                        request.externalReference()
                                )
                        )
                        .processorReference(
                                trimToNull(
                                        request.processorReference()
                                )
                        )
                        .idempotencyKey(
                                transactionIdempotencyKey
                        )
                        .notes(
                                "Refund transaction for "
                                        + refund.getRefundNumber()
                        )
                        .build();

        try {
            return billingPaymentTransactionRepository
                    .saveAndFlush(transaction);

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingPaymentTransaction concurrent =
                    billingPaymentTransactionRepository
                            .findByIdempotencyKey(
                                    transactionIdempotencyKey
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            throw new BadRequestAlertException(
                    "Unable to create refund payment transaction.",
                    ENTITY_NAME,
                    "refundPaymentTransaction.create.failed"
            );
        }
    }

    private void completeRefund(
            BillingRefund refund,
            BillingPaymentTransaction refundTransaction,
            BigDecimal amount,
            String completedBy
    ) {
        refund.setRefundPaymentTransaction(
                refundTransaction
        );

        refund.setRefundedAmount(
                amount
        );

        refund.setStatus(
                BillingRefundStatus.COMPLETED
        );

        refund.setCompletedBy(
                completedBy.trim()
        );

        refund.setCompletedDate(
                Instant.now()
        );

        billingRefundRepository.save(
                refund
        );
    }

    private BigDecimal resolveRefundableAmount(
            BillingRefundRequest request,
            BillingWallet wallet,
            BillingPayment originalPayment
    ) {
        BigDecimal requested =
                positiveMoney(
                        request.requestedAmount()
                );

        BigDecimal walletAvailable =
                money(
                        wallet.getAvailableBalance()
                );

        if (request.refundSourceType()
                == BillingRefundSourceType.ORIGINAL_PAYMENT) {

            if (originalPayment == null) {
                throw new BadRequestAlertException(
                        "Original payment is required for payment-source refund.",
                        ENTITY_NAME,
                        "originalPayment.required"
                );
            }

            BigDecimal remainingPaymentRefundable =
                    calculatePaymentRefundableAmount(
                            originalPayment
                    );

            BigDecimal amount =
                    minimum(
                            requested,
                            walletAvailable,
                            remainingPaymentRefundable
                    );

            if (amount.signum() <= 0) {
                throw new BadRequestAlertException(
                        "No refundable amount remains for the original payment.",
                        ENTITY_NAME,
                        "payment.refundable.zero"
                );
            }

            /*
             * Reject instead of silently doing a partial refund.
             */
            if (amount.compareTo(requested) < 0) {
                throw new BadRequestAlertException(
                        "Requested refund exceeds the currently refundable amount. "
                                + "Refundable amount: "
                                + amount,
                        ENTITY_NAME,
                        "refund.exceedsRefundable"
                );
            }

            return amount;
        }

        if (walletAvailable.compareTo(
                requested
        ) < 0) {
            throw new BadRequestAlertException(
                    "Requested refund exceeds available wallet balance. "
                            + "Reserved or consumed funds must be released or reversed first.",
                    ENTITY_NAME,
                    "refund.exceedsAvailable"
            );
        }

        return requested;
    }

    private BigDecimal calculatePaymentRefundableAmount(
            BillingPayment payment
    ) {
        BigDecimal completedRefunds =
                billingRefundRepository
                        .findAllByOriginalPayment_IdAndStatusInOrderByRequestDateAscIdAsc(
                                payment.getId(),
                                FINANCIALLY_COMPLETED_REFUND_STATUSES
                        )
                        .stream()
                        .map(refund ->
                                money(
                                        refund.getRefundedAmount()
                                ).subtract(
                                        money(
                                                refund.getReversedAmount()
                                        )
                                )
                        )
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        return money(
                payment.getAmount()
        ).subtract(
                completedRefunds
        ).max(zero());
    }

    private BillingPayment loadOriginalPayment(
            BillingRefundRequest request,
            Patient patient
    ) {
        if (request.originalPaymentId() == null) {
            if (request.refundSourceType()
                    == BillingRefundSourceType.ORIGINAL_PAYMENT) {
                throw new BadRequestAlertException(
                        "Original payment ID is required.",
                        ENTITY_NAME,
                        "originalPaymentId.required"
                );
            }

            return null;
        }

        BillingPayment payment =
                billingPaymentRepository
                        .findById(
                                request.originalPaymentId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Original billing payment not found with id "
                                                + request.originalPaymentId(),
                                        ENTITY_NAME,
                                        "originalPayment.notfound"
                                )
                        );

        if (payment.getPatient() == null
                || !payment.getPatient()
                .getId()
                .equals(patient.getId())) {
            throw new BadRequestAlertException(
                    "Original payment does not belong to the refund patient.",
                    ENTITY_NAME,
                    "originalPayment.patient.mismatch"
            );
        }

        if (payment.getStatus()
                != BillingPaymentStatus.COMPLETED
                && payment.getStatus()
                != BillingPaymentStatus.REFUNDED) {
            throw new BadRequestAlertException(
                    "Only completed payments may be refunded.",
                    ENTITY_NAME,
                    "originalPayment.notCompleted"
            );
        }

        return payment;
    }

    private BillingPaymentTransaction
    loadOriginalPaymentTransaction(
            BillingRefundRequest request,
            BillingPayment payment
    ) {
        if (request.originalPaymentTransactionId()
                == null) {
            return null;
        }

        BillingPaymentTransaction transaction =
                billingPaymentTransactionRepository
                        .findById(
                                request
                                        .originalPaymentTransactionId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Original payment transaction not found with id "
                                                + request
                                                .originalPaymentTransactionId(),
                                        ENTITY_NAME,
                                        "originalPaymentTransaction.notfound"
                                )
                        );

        if (payment == null
                || transaction.getPayment() == null
                || !transaction.getPayment()
                .getId()
                .equals(payment.getId())) {
            throw new BadRequestAlertException(
                    "Original payment transaction does not belong to the original payment.",
                    ENTITY_NAME,
                    "originalPaymentTransaction.payment.mismatch"
            );
        }

        return transaction;
    }

    private BillingWallet resolveAndLockWallet(
            BillingRefundRequest request,
            BillingPayment originalPayment
    ) {
        BillingWallet wallet =
                billingWalletService.lockWallet(
                        request.patientId()
                );

        if (originalPayment != null) {
            if (originalPayment.getPatient() == null
                    || !originalPayment
                    .getPatient()
                    .getId()
                    .equals(request.patientId())) {

                throw new BadRequestAlertException(
                        "Original payment does not belong to the refund patient.",
                        ENTITY_NAME,
                        "originalPayment.patient.mismatch"
                );
            }

            if (originalPayment.getWallet() == null
                    || !originalPayment
                    .getWallet()
                    .getId()
                    .equals(wallet.getId())) {

                throw new BadRequestAlertException(
                        "Original payment does not belong to the patient's wallet.",
                        ENTITY_NAME,
                        "originalPayment.wallet.mismatch"
                );
            }

            if (originalPayment.getCurrency()
                    != wallet.getCurrency()) {

                throw new BadRequestAlertException(
                        "Original payment currency does not match organization currency.",
                        ENTITY_NAME,
                        "originalPayment.currency.mismatch"
                );
            }
        }

        return wallet;
    }

    private void validateWalletPatient(
            BillingWallet wallet,
            Patient patient
    ) {
        if (wallet.getPatient() == null
                || !wallet.getPatient()
                .getId()
                .equals(patient.getId())) {
            throw new BadRequestAlertException(
                    "Refund wallet does not belong to the patient.",
                    ENTITY_NAME,
                    "wallet.patient.mismatch"
            );
        }
    }

    private PatientEncounter loadEncounter(
            Long encounterId,
            Patient patient
    ) {
        if (encounterId == null) {
            return null;
        }

        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(encounterId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id "
                                                + encounterId,
                                        ENTITY_NAME,
                                        "encounter.notfound"
                                )
                        );

        if (encounter.getPatient() == null
                || !encounter.getPatient()
                .getId()
                .equals(patient.getId())) {
            throw new BadRequestAlertException(
                    "Encounter does not belong to the refund patient.",
                    ENTITY_NAME,
                    "encounter.patient.mismatch"
            );
        }

        return encounter;
    }

    private BillingRefund saveRefund(
            BillingRefund refund,
            String idempotencyKey
    ) {
        try {
            return billingRefundRepository
                    .saveAndFlush(refund);

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingRefund concurrent =
                    billingRefundRepository
                            .findByIdempotencyKey(
                                    idempotencyKey
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            throw new BadRequestAlertException(
                    "Unable to create billing refund.",
                    ENTITY_NAME,
                    "refund.create.failed"
            );
        }
    }

    private void updateOriginalPaymentStatus(
            BillingPayment payment
    ) {
        if (payment == null) {
            return;
        }

        BigDecimal remainingRefundable =
                calculatePaymentRefundableAmount(
                        payment
                );

        if (remainingRefundable.signum() == 0) {
            payment.setStatus(
                    BillingPaymentStatus.REFUNDED
            );
        } else if (payment.getStatus()
                == BillingPaymentStatus.REFUNDED) {
            payment.setStatus(
                    BillingPaymentStatus.COMPLETED
            );
        }

        billingPaymentRepository.save(payment);
    }

    private void recordRefundLedger(
            BillingRefund refund,
            BillingWallet wallet,
            BillingPaymentTransaction refundTransaction,
            BigDecimal availableBefore,
            BigDecimal refundedBefore,
            BillingRefundRequest request
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        refund.getTransactionGroupId(),
                        request.requestId().trim(),
                        refund.getIdempotencyKey()
                                + ":LEDGER",

                        refund.getPatient(),
                        refund.getEncounter(),

                        wallet,
                        refund.getOriginalPayment(),
                        refundTransaction,

                        null,
                        null,
                        null,

                        null,
                        null,

                        null,
                        null,
                        refund,

                        BillingLedgerTransactionType
                                .REFUND_COMPLETED,

                        BillingLedgerScope.REFUND,

                        refund.getRefundedAmount(),
                        refund.getCurrency(),

                        refund.getRefundedAmount()
                                .negate(),

                        zero(),

                        zero(),

                        refund.getRefundedAmount(),

                        zero(),
                        zero(),
                        zero(),

                        availableBefore,
                        money(
                                wallet.getAvailableBalance()
                        ),

                        money(
                                wallet.getReservedBalance()
                        ),
                        money(
                                wallet.getReservedBalance()
                        ),

                        null,
                        null,

                        null,
                        null,

                        BillingLedgerEntryDirection.CREDIT,
                        BillingLedgerEntryCategory.BUSINESS,

                        null,

                        "BILLING_REFUND",
                        refund.getId(),
                        refund.getRefundNumber(),

                        "Available wallet balance refunded to patient.",

                        refund.getReason(),

                        request.sourceChannel()
                )
        );
    }

    private void recordRefundReversalLedger(
            BillingRefund originalRefund,
            BillingRefund reversalRefund,
            BillingWallet wallet,
            BigDecimal amount,
            BigDecimal availableBefore,
            BigDecimal refundedBefore,
            String requestId,
            String reason,
            BillingLedgerSourceChannel sourceChannel
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        reversalRefund
                                .getTransactionGroupId(),

                        requestId.trim(),

                        reversalRefund
                                .getIdempotencyKey()
                                + ":LEDGER",

                        reversalRefund.getPatient(),

                        reversalRefund.getEncounter(),

                        wallet,

                        reversalRefund
                                .getOriginalPayment(),

                        null,

                        null,
                        null,
                        null,

                        null,
                        null,

                        null,
                        null,
                        reversalRefund,

                        BillingLedgerTransactionType
                                .REFUND_REVERSED,

                        BillingLedgerScope.REFUND,

                        amount,

                        reversalRefund.getCurrency(),

                        amount,

                        zero(),

                        zero(),

                        amount.negate(),

                        zero(),
                        zero(),
                        zero(),

                        availableBefore,

                        money(
                                wallet.getAvailableBalance()
                        ),

                        money(
                                wallet.getReservedBalance()
                        ),

                        money(
                                wallet.getReservedBalance()
                        ),

                        null,
                        null,

                        null,
                        null,

                        BillingLedgerEntryDirection.DEBIT,

                        BillingLedgerEntryCategory.REVERSAL,

                        null,

                        "BILLING_REFUND",

                        originalRefund.getId(),

                        originalRefund.getRefundNumber(),

                        "Refund reversed and amount returned to available wallet balance.",

                        reason.trim(),

                        sourceChannel
                )
        );
    }

    private BillingRefundResult buildResult(
            BillingRefund refund
    ) {
        BillingWallet wallet =
                refund.getWallet();

        return new BillingRefundResult(
                refund.getId(),

                refund.getRefundNumber(),

                refund.getRefundPaymentTransaction()
                        == null
                        ? null
                        : refund
                        .getRefundPaymentTransaction()
                        .getId(),

                refund.getRefundPaymentTransaction()
                        == null
                        ? null
                        : refund
                        .getRefundPaymentTransaction()
                        .getTransactionNumber(),

                wallet.getId(),

                refund.getOriginalPayment() == null
                        ? null
                        : refund.getOriginalPayment().getId(),

                money(
                        refund.getRequestedAmount()
                ),

                money(
                        refund.getApprovedAmount()
                ),

                money(
                        refund.getRefundedAmount()
                ),

                money(
                        refund.getReversedAmount()
                ),

                money(
                        wallet.getAvailableBalance()
                ),

                money(
                        wallet.getReservedBalance()
                ),

                money(
                        wallet.getConsumedAmount()
                ),

                money(
                        wallet.getRefundedAmount()
                ),

                refund.getCurrency(),

                refund.getRefundSourceType(),

                refund.getStatus()
        );
    }

    private BillingRefundReversalResult
    buildReversalResult(
            BillingRefund originalRefund,
            BillingRefund reversalRefund
    ) {
        BigDecimal remaining =
                money(
                        originalRefund
                                .getRefundedAmount()
                ).subtract(
                        money(
                                originalRefund
                                        .getReversedAmount()
                        )
                ).max(zero());

        return new BillingRefundReversalResult(
                originalRefund.getId(),

                reversalRefund.getId(),

                reversalRefund.getRefundNumber(),

                money(
                        reversalRefund.getReversedAmount()
                ),

                remaining,

                money(
                        originalRefund
                                .getWallet()
                                .getAvailableBalance()
                ),

                money(
                        originalRefund
                                .getWallet()
                                .getRefundedAmount()
                ),

                originalRefund.getStatus(),

                reversalRefund.getStatus()
        );
    }

    private void validateRefundRequest(
            BillingRefundRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Refund request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.patientId() == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patientId.required"
            );
        }

        if (request.refundSourceType() == null) {
            throw new BadRequestAlertException(
                    "Refund source type is required.",
                    ENTITY_NAME,
                    "refundSourceType.required"
            );
        }

        positiveMoney(
                request.requestedAmount()
        );

        if (request.refundMethodId() == null) {
            throw new BadRequestAlertException(
                    "Refund method ID is required.",
                    ENTITY_NAME,
                    "refundMethodId.required"
            );
        }

        if (request.refundMethodCode() == null
                || request.refundMethodCode()
                .isBlank()) {
            throw new BadRequestAlertException(
                    "Refund method code is required.",
                    ENTITY_NAME,
                    "refundMethodCode.required"
            );
        }

        if (request.requestedBy() == null
                || request.requestedBy()
                .isBlank()) {
            throw new BadRequestAlertException(
                    "Requested-by user is required.",
                    ENTITY_NAME,
                    "requestedBy.required"
            );
        }

        if (request.reason() == null
                || request.reason().isBlank()) {
            throw new BadRequestAlertException(
                    "Refund reason is required.",
                    ENTITY_NAME,
                    "reason.required"
            );
        }

        if (request.requestId() == null
                || request.requestId()
                .isBlank()) {
            throw new BadRequestAlertException(
                    "Request ID is required.",
                    ENTITY_NAME,
                    "requestId.required"
            );
        }

        if (request.sourceChannel() == null) {
            throw new BadRequestAlertException(
                    "Ledger source channel is required.",
                    ENTITY_NAME,
                    "sourceChannel.required"
            );
        }
    }

    private void validateRefundReversalInput(
            Long refundId,
            BigDecimal requestedAmount,
            String reason,
            String reversedBy,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (refundId == null) {
            throw new BadRequestAlertException(
                    "Refund ID is required.",
                    ENTITY_NAME,
                    "refundId.required"
            );
        }

        positiveMoney(requestedAmount);

        if (reason == null
                || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Refund reversal reason is required.",
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
                    "Source channel is required.",
                    ENTITY_NAME,
                    "sourceChannel.required"
            );
        }
    }

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

    private String trimToNull(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String generateRefundNumber() {
        return "RFD-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private String generateTransactionNumber() {
        return "PTX-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }
}