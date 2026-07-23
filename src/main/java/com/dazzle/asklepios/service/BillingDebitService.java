package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingDebitAccount;
import com.dazzle.asklepios.domain.BillingDebitTransaction;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitAccountStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryCategory;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryDirection;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerScope;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerTransactionType;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.BillingDebitAccountRepository;
import com.dazzle.asklepios.repository.BillingDebitTransactionRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.billing.BillingAllocationReversalResult;
import com.dazzle.asklepios.service.dto.billing.BillingDebitCreationResult;
import com.dazzle.asklepios.service.dto.billing.BillingDebitReversalResult;
import com.dazzle.asklepios.service.dto.billing.BillingDebitSettlementResult;
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
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingDebitService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingDebitService.class
            );

    private static final String ENTITY_NAME =
            "billingDebit";

    private static final int MONEY_SCALE = 4;

    private final BillingDebitAccountRepository
            billingDebitAccountRepository;

    private final BillingDebitTransactionRepository
            billingDebitTransactionRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final PatientRepository
            patientRepository;

    private final BillingLedgerService
            billingLedgerService;
    private final BillingAllocationRepository billingAllocationRepository;
    private final BillingAllocationService billingAllocationService;

    /*
     * Creates or loads one debit account per patient/currency.
     *
     * creditLimit and permissions should be resolved by CheckoutService
     * from Billing Configuration before calling this method.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingDebitAccount getOrCreateAccount(
            Long patientId,
            Currency currency,
            BigDecimal creditLimit,
            Boolean debitAllowed,
            Boolean approvalRequired,
            String approvedBy
    ) {
        validatePatientAndCurrency(
                patientId,
                currency
        );

        BigDecimal normalizedCreditLimit =
                nonNegativeMoney(
                        creditLimit,
                        "Credit limit"
                );

        BillingDebitAccount existing =
                billingDebitAccountRepository
                        .findByPatient_IdAndCurrency(
                                patientId,
                                currency
                        )
                        .orElse(null);

        if (existing != null) {
            validateAccountBalance(existing);
            return existing;
        }

        Patient patient =
                patientRepository
                        .findById(patientId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient not found with id "
                                                + patientId,
                                        ENTITY_NAME,
                                        "patient.notfound"
                                )
                        );

        boolean approvalNeeded =
                Boolean.TRUE.equals(
                        approvalRequired
                );

        String normalizedApprovedBy =
                trimToNull(approvedBy);

        if (approvalNeeded
                && normalizedApprovedBy == null) {
            throw new BadRequestAlertException(
                    "Approved-by user is required when debit approval is required.",
                    ENTITY_NAME,
                    "approvedBy.required"
            );
        }

        BillingDebitAccount account =
                BillingDebitAccount.builder()
                        .accountNumber(
                                generateAccountNumber()
                        )
                        .patient(patient)
                        .currency(currency)
                        .creditLimit(
                                normalizedCreditLimit
                        )
                        .currentDebitBalance(
                                zero()
                        )
                        .availableCredit(
                                normalizedCreditLimit
                        )
                        .totalDebitCreated(
                                zero()
                        )
                        .totalDebitSettled(
                                zero()
                        )
                        .status(
                                BillingDebitAccountStatus.ACTIVE
                        )
                        .debitAllowed(
                                Boolean.TRUE.equals(
                                        debitAllowed
                                )
                        )
                        .approvalRequired(
                                approvalNeeded
                        )
                        .approvedBy(
                                normalizedApprovedBy
                        )
                        .approvedDate(
                                normalizedApprovedBy == null
                                        ? null
                                        : Instant.now()
                        )
                        .build();

        try {
            BillingDebitAccount saved =
                    billingDebitAccountRepository
                            .saveAndFlush(account);

            LOG.info(
                    "[CREATE_ACCOUNT] Debit account created "
                            + "accountId={} accountNumber={} "
                            + "patientId={} currency={} creditLimit={}",
                    saved.getId(),
                    saved.getAccountNumber(),
                    patientId,
                    currency,
                    saved.getCreditLimit()
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingDebitAccount concurrent =
                    billingDebitAccountRepository
                            .findByPatient_IdAndCurrency(
                                    patientId,
                                    currency
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            LOG.error(
                    "[CREATE_ACCOUNT] Debit account creation failed "
                            + "patientId={} currency={}",
                    patientId,
                    currency,
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to create billing debit account.",
                    ENTITY_NAME,
                    "debitAccount.create.failed"
            );
        }
    }

    /*
     * Creates debt for the outstanding patient responsibility.
     *
     * Important:
     * This creates the debit account transaction only.
     * BillingCheckoutService will then create a DEBIT allocation
     * against the responsibility using this transaction.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingDebitCreationResult createDebit(
            Long chargeResponsibilityId,
            BigDecimal requestedAmount,
            BigDecimal configuredCreditLimit,
            Boolean debitAllowed,
            Boolean approvalRequired,
            String approvedBy,
            LocalDate dueDate,
            String reason,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateCreateDebitInput(
                chargeResponsibilityId,
                requestedAmount,
                requestId,
                sourceChannel
        );

        String idempotencyKey =
                "DEBIT:CREATE:RESPONSIBILITY:"
                        + chargeResponsibilityId
                        + ":"
                        + requestId.trim();

        BillingDebitTransaction existing =
                billingDebitTransactionRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            return buildCreationResult(existing);
        }

        BillingChargeResponsibility responsibility =
                billingChargeResponsibilityRepository
                        .findById(
                                chargeResponsibilityId
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing responsibility not found with id "
                                                + chargeResponsibilityId,
                                        ENTITY_NAME,
                                        "responsibility.notfound"
                                )
                        );

        BigDecimal outstanding =
                money(
                        responsibility.getOutstandingAmount()
                );

        if (outstanding.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Responsibility has no outstanding amount.",
                    ENTITY_NAME,
                    "responsibility.outstanding.zero"
            );
        }

        BillingDebitAccount account =
                getOrCreateAccount(
                        responsibility
                                .getPatient()
                                .getId(),
                        responsibility.getCurrency(),
                        configuredCreditLimit,
                        debitAllowed,
                        approvalRequired,
                        approvedBy
                );

        account =
                lockAccount(account.getId());

        validateAccountForDebit(
                account,
                approvedBy
        );

        BigDecimal requested =
                positiveMoney(
                        requestedAmount,
                        "Debit amount"
                );

        BigDecimal amount =
                minimum(
                        requested,
                        outstanding,
                        money(
                                account.getAvailableCredit()
                        )
                );

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "No available credit can be used for this responsibility.",
                    ENTITY_NAME,
                    "availableCredit.zero"
            );
        }

        BigDecimal balanceBefore =
                money(
                        account.getCurrentDebitBalance()
                );

        BigDecimal balanceAfter =
                balanceBefore.add(amount);

        BigDecimal availableCreditAfter =
                money(
                        account.getCreditLimit()
                ).subtract(balanceAfter);

        if (availableCreditAfter.signum() < 0) {
            throw new BadRequestAlertException(
                    "Debit amount exceeds the patient's available credit.",
                    ENTITY_NAME,
                    "creditLimit.exceeded"
            );
        }

        account.setCurrentDebitBalance(
                balanceAfter
        );

        account.setAvailableCredit(
                availableCreditAfter
        );

        account.setTotalDebitCreated(
                money(
                        account.getTotalDebitCreated()
                ).add(amount)
        );

        validateAccountBalance(account);

        billingDebitAccountRepository.save(account);

        UUID transactionGroupId =
                UUID.randomUUID();

        BillingDebitTransaction transaction =
                BillingDebitTransaction.builder()
                        .transactionNumber(
                                generateTransactionNumber()
                        )
                        .debitAccount(account)
                        .patient(
                                responsibility.getPatient()
                        )
                        .encounter(
                                responsibility.getEncounter()
                        )
                        .chargeResponsibility(
                                responsibility
                        )
                        .charge(
                                responsibility.getCharge()
                        )
                        .chargeLine(
                                responsibility.getChargeLine()
                        )
                        .patientServiceProduct(
                                responsibility
                                        .getPatientServiceProduct()
                        )
                        .payment(null)
                        .paymentTransaction(null)
                        .parentTransaction(null)
                        .transactionType(
                                BillingDebitTransactionType
                                        .DEBIT_CREATED
                        )
                        .amount(amount)
                        .currency(
                                responsibility.getCurrency()
                        )
                        .balanceBefore(
                                balanceBefore
                        )
                        .balanceAfter(
                                balanceAfter
                        )
                        .status(
                                BillingDebitTransactionStatus
                                        .COMPLETED
                        )
                        .transactionDate(
                                Instant.now()
                        )
                        .dueDate(dueDate)
                        .referenceType(
                                "BILLING_RESPONSIBILITY"
                        )
                        .referenceId(
                                responsibility.getId()
                        )
                        .referenceNumber(null)
                        .reason(
                                trimToNull(reason)
                        )
                        .approvedBy(
                                trimToNull(approvedBy)
                        )
                        .approvedDate(
                                trimToNull(approvedBy) == null
                                        ? null
                                        : Instant.now()
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                transactionGroupId
                        )
                        .notes(
                                "Patient debit created for outstanding billing responsibility."
                        )
                        .build();

        BillingDebitTransaction savedTransaction =
                saveTransaction(
                        transaction,
                        idempotencyKey
                );

        recordDebitCreatedLedger(
                savedTransaction,
                balanceBefore,
                balanceAfter,
                sourceChannel,
                requestId
        );

        LOG.info(
                "[CREATE_DEBIT] Patient debit created "
                        + "accountId={} transactionId={} "
                        + "responsibilityId={} amount={} "
                        + "balanceBefore={} balanceAfter={}",
                account.getId(),
                savedTransaction.getId(),
                responsibility.getId(),
                amount,
                balanceBefore,
                balanceAfter
        );

        return buildCreationResult(
                savedTransaction
        );
    }

    /*
     * Settles patient debit using a confirmed payment.
     *
     * This operation reduces the debit account balance.
     * The payment must already be confirmed and credited/registered.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingDebitSettlementResult settleDebit(
            Long debitAccountId,
            BigDecimal requestedAmount,
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateSettlementInput(
                debitAccountId,
                requestedAmount,
                payment,
                requestId,
                sourceChannel
        );

        String idempotencyKey =
                "DEBIT:SETTLEMENT:ACCOUNT:"
                        + debitAccountId
                        + ":"
                        + requestId.trim();

        BillingDebitTransaction existing =
                billingDebitTransactionRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            return buildSettlementResult(existing);
        }

        BillingDebitAccount account =
                lockAccount(debitAccountId);

        validateAccountUsable(account);

        if (!account.getPatient()
                .getId()
                .equals(
                        payment.getPatient()
                                .getId()
                )) {
            throw new BadRequestAlertException(
                    "Payment does not belong to the debit-account patient.",
                    ENTITY_NAME,
                    "payment.patient.mismatch"
            );
        }

        if (account.getCurrency()
                != payment.getCurrency()) {
            throw new BadRequestAlertException(
                    "Payment currency does not match debit-account currency.",
                    ENTITY_NAME,
                    "payment.currency.mismatch"
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

        BigDecimal balanceBefore =
                money(
                        account.getCurrentDebitBalance()
                );

        if (balanceBefore.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Debit account has no outstanding balance.",
                    ENTITY_NAME,
                    "debitBalance.zero"
            );
        }

        BigDecimal amount =
                minimum(
                        positiveMoney(
                                requestedAmount,
                                "Settlement amount"
                        ),
                        balanceBefore,
                        money(payment.getAmount())
                );

        BigDecimal balanceAfter =
                balanceBefore.subtract(amount);

        account.setCurrentDebitBalance(
                balanceAfter
        );

        account.setAvailableCredit(
                money(
                        account.getCreditLimit()
                ).subtract(balanceAfter)
        );

        account.setTotalDebitSettled(
                money(
                        account.getTotalDebitSettled()
                ).add(amount)
        );

        validateAccountBalance(account);

        billingDebitAccountRepository.save(account);

        BillingDebitTransactionType type =
                balanceAfter.signum() == 0
                        ? BillingDebitTransactionType
                        .DEBIT_SETTLEMENT
                        : BillingDebitTransactionType
                        .PARTIAL_SETTLEMENT;

        BillingDebitTransaction transaction =
                BillingDebitTransaction.builder()
                        .transactionNumber(
                                generateTransactionNumber()
                        )
                        .debitAccount(account)
                        .patient(
                                account.getPatient()
                        )
                        .encounter(
                                payment.getEncounter()
                        )
                        .payment(payment)
                        .paymentTransaction(
                                paymentTransaction
                        )
                        .parentTransaction(null)
                        .transactionType(type)
                        .amount(amount)
                        .currency(
                                account.getCurrency()
                        )
                        .balanceBefore(
                                balanceBefore
                        )
                        .balanceAfter(
                                balanceAfter
                        )
                        .status(
                                BillingDebitTransactionStatus
                                        .COMPLETED
                        )
                        .transactionDate(
                                Instant.now()
                        )
                        .settledDate(
                                Instant.now()
                        )
                        .referenceType(
                                "BILLING_PAYMENT"
                        )
                        .referenceId(
                                payment.getId()
                        )
                        .referenceNumber(
                                payment.getPaymentNumber()
                        )
                        .reason(
                                "Patient debit settlement"
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                UUID.randomUUID()
                        )
                        .notes(
                                "Debit account settled using billing payment."
                        )
                        .build();

        BillingDebitTransaction savedTransaction =
                saveTransaction(
                        transaction,
                        idempotencyKey
                );

        recordDebitSettlementLedger(
                savedTransaction,
                payment,
                paymentTransaction,
                balanceBefore,
                balanceAfter,
                sourceChannel,
                requestId
        );

        LOG.info(
                "[SETTLE_DEBIT] Debit settled "
                        + "accountId={} transactionId={} "
                        + "amount={} balanceBefore={} balanceAfter={}",
                account.getId(),
                savedTransaction.getId(),
                amount,
                balanceBefore,
                balanceAfter
        );

        return buildSettlementResult(
                savedTransaction
        );
    }

    private void recordDebitCreatedLedger(
            BillingDebitTransaction transaction,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            BillingLedgerSourceChannel sourceChannel,
            String requestId
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        transaction.getTransactionGroupId(),
                        requestId.trim(),
                        transaction.getIdempotencyKey()
                                + ":LEDGER",

                        transaction.getPatient(),
                        transaction.getEncounter(),

                        null,
                        null,
                        null,

                        transaction.getCharge(),
                        transaction.getChargeLine(),
                        transaction.getChargeResponsibility(),

                        null,
                        null,

                        transaction.getDebitAccount(),
                        transaction,
                        null,

                        BillingLedgerTransactionType
                                .DEBIT_CREATED,

                        BillingLedgerScope.DEBIT_ACCOUNT,

                        transaction.getAmount(),
                        transaction.getCurrency(),

                        zero(),
                        zero(),
                        zero(),
                        zero(),

                        transaction.getAmount(),

                        zero(),
                        zero(),

                        null,
                        null,
                        null,
                        null,

                        balanceBefore,
                        balanceAfter,

                        transaction
                                .getChargeResponsibility()
                                == null
                                ? null
                                : money(
                                transaction
                                        .getChargeResponsibility()
                                        .getOutstandingAmount()
                        ),

                        transaction
                                .getChargeResponsibility()
                                == null
                                ? null
                                : money(
                                transaction
                                        .getChargeResponsibility()
                                        .getOutstandingAmount()
                        ),

                        BillingLedgerEntryDirection.DEBIT,
                        BillingLedgerEntryCategory.BUSINESS,

                        null,

                        "BILLING_DEBIT_TRANSACTION",
                        transaction.getId(),
                        transaction.getTransactionNumber(),

                        "Patient debit created.",

                        transaction.getReason(),

                        sourceChannel
                )
        );
    }

    private void recordDebitSettlementLedger(
            BillingDebitTransaction transaction,
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            BillingLedgerSourceChannel sourceChannel,
            String requestId
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        transaction.getTransactionGroupId(),
                        requestId.trim(),
                        transaction.getIdempotencyKey()
                                + ":LEDGER",

                        transaction.getPatient(),
                        transaction.getEncounter(),

                        payment.getWallet(),
                        payment,
                        paymentTransaction,

                        null,
                        null,
                        null,

                        null,
                        null,

                        transaction.getDebitAccount(),
                        transaction,
                        null,

                        BillingLedgerTransactionType
                                .DEBIT_SETTLED,

                        BillingLedgerScope.DEBIT_ACCOUNT,

                        transaction.getAmount(),
                        transaction.getCurrency(),

                        zero(),
                        zero(),
                        zero(),
                        zero(),

                        transaction.getAmount()
                                .negate(),

                        zero(),
                        zero(),

                        payment.getWallet() == null
                                ? null
                                : money(
                                payment.getWallet()
                                        .getAvailableBalance()
                        ),

                        payment.getWallet() == null
                                ? null
                                : money(
                                payment.getWallet()
                                        .getAvailableBalance()
                        ),

                        payment.getWallet() == null
                                ? null
                                : money(
                                payment.getWallet()
                                        .getReservedBalance()
                        ),

                        payment.getWallet() == null
                                ? null
                                : money(
                                payment.getWallet()
                                        .getReservedBalance()
                        ),

                        balanceBefore,
                        balanceAfter,

                        null,
                        null,

                        BillingLedgerEntryDirection.CREDIT,
                        BillingLedgerEntryCategory.BUSINESS,

                        null,

                        "BILLING_DEBIT_TRANSACTION",
                        transaction.getId(),
                        transaction.getTransactionNumber(),

                        "Patient debit settled using payment.",

                        transaction.getReason(),

                        sourceChannel
                )
        );
    }

    private BillingDebitTransaction saveTransaction(
            BillingDebitTransaction transaction,
            String idempotencyKey
    ) {
        try {
            return billingDebitTransactionRepository
                    .saveAndFlush(transaction);

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingDebitTransaction concurrent =
                    billingDebitTransactionRepository
                            .findByIdempotencyKey(
                                    idempotencyKey
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            LOG.error(
                    "[SAVE_TRANSACTION] Debit transaction creation failed "
                            + "idempotencyKey={} type={} amount={}",
                    idempotencyKey,
                    transaction.getTransactionType(),
                    transaction.getAmount(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to create billing debit transaction.",
                    ENTITY_NAME,
                    "debitTransaction.create.failed"
            );
        }
    }

    private BillingDebitAccount lockAccount(
            Long accountId
    ) {
        return billingDebitAccountRepository
                .findById(accountId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Billing debit account not found with id "
                                        + accountId,
                                ENTITY_NAME,
                                "debitAccount.notfound"
                        )
                );
    }

    private void validateAccountForDebit(
            BillingDebitAccount account,
            String approvedBy
    ) {
        validateAccountUsable(account);

        if (!Boolean.TRUE.equals(
                account.getDebitAllowed()
        )) {
            throw new BadRequestAlertException(
                    "Debit is not allowed for this patient.",
                    ENTITY_NAME,
                    "debit.notAllowed"
            );
        }

        if (Boolean.TRUE.equals(
                account.getApprovalRequired()
        )
                && trimToNull(approvedBy) == null
                && trimToNull(
                account.getApprovedBy()
        ) == null) {

            throw new BadRequestAlertException(
                    "Debit requires approval.",
                    ENTITY_NAME,
                    "debit.approval.required"
            );
        }

        if (money(
                account.getAvailableCredit()
        ).signum() <= 0) {
            throw new BadRequestAlertException(
                    "Patient has no available credit.",
                    ENTITY_NAME,
                    "availableCredit.zero"
            );
        }
    }

    private void validateAccountUsable(
            BillingDebitAccount account
    ) {
        if (account.getStatus()
                != BillingDebitAccountStatus.ACTIVE) {
            throw new BadRequestAlertException(
                    "Debit account is not active.",
                    ENTITY_NAME,
                    "debitAccount.notActive"
            );
        }

        if (account.getExpiryDate() != null
                && account.getExpiryDate()
                .isBefore(LocalDate.now())) {

            throw new BadRequestAlertException(
                    "Debit account has expired.",
                    ENTITY_NAME,
                    "debitAccount.expired"
            );
        }

        validateAccountBalance(account);
    }

    private void validateAccountBalance(
            BillingDebitAccount account
    ) {
        BigDecimal creditLimit =
                money(
                        account.getCreditLimit()
                );

        BigDecimal currentBalance =
                money(
                        account.getCurrentDebitBalance()
                );

        BigDecimal availableCredit =
                money(
                        account.getAvailableCredit()
                );

        if (creditLimit.signum() < 0
                || currentBalance.signum() < 0
                || availableCredit.signum() < 0) {
            throw new BadRequestAlertException(
                    "Debit account contains a negative balance.",
                    ENTITY_NAME,
                    "debitAccount.balance.negative"
            );
        }

        if (currentBalance.compareTo(
                creditLimit
        ) > 0) {
            throw new BadRequestAlertException(
                    "Debit balance exceeds credit limit.",
                    ENTITY_NAME,
                    "debitAccount.creditLimit.exceeded"
            );
        }

        BigDecimal expectedAvailable =
                creditLimit.subtract(
                        currentBalance
                );

        if (availableCredit.compareTo(
                expectedAvailable
        ) != 0) {
            throw new BadRequestAlertException(
                    "Debit account available credit is inconsistent.",
                    ENTITY_NAME,
                    "debitAccount.balance.invalid"
            );
        }

        account.setCreditLimit(
                creditLimit
        );

        account.setCurrentDebitBalance(
                currentBalance
        );

        account.setAvailableCredit(
                availableCredit
        );

        account.setTotalDebitCreated(
                money(
                        account.getTotalDebitCreated()
                )
        );

        account.setTotalDebitSettled(
                money(
                        account.getTotalDebitSettled()
                )
        );
    }

    private void validateCreateDebitInput(
            Long responsibilityId,
            BigDecimal amount,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (responsibilityId == null) {
            throw new BadRequestAlertException(
                    "Responsibility ID is required.",
                    ENTITY_NAME,
                    "responsibilityId.required"
            );
        }

        positiveMoney(
                amount,
                "Debit amount"
        );

        validateRequestInfo(
                requestId,
                sourceChannel
        );
    }

    private void validateSettlementInput(
            Long accountId,
            BigDecimal amount,
            BillingPayment payment,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (accountId == null) {
            throw new BadRequestAlertException(
                    "Debit-account ID is required.",
                    ENTITY_NAME,
                    "debitAccountId.required"
            );
        }

        positiveMoney(
                amount,
                "Settlement amount"
        );

        if (payment == null
                || payment.getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted payment is required.",
                    ENTITY_NAME,
                    "payment.required"
            );
        }

        validateRequestInfo(
                requestId,
                sourceChannel
        );
    }

    private void validateRequestInfo(
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

    private void validatePatientAndCurrency(
            Long patientId,
            Currency currency
    ) {
        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patientId.required"
            );
        }

        if (currency == null) {
            throw new BadRequestAlertException(
                    "Currency is required.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }
    }

    private BillingDebitCreationResult buildCreationResult(
            BillingDebitTransaction transaction
    ) {
        BillingDebitAccount account =
                transaction.getDebitAccount();

        return new BillingDebitCreationResult(
                account.getId(),
                account.getAccountNumber(),

                transaction.getId(),
                transaction.getTransactionNumber(),

                transaction.getChargeResponsibility()
                        == null
                        ? null
                        : transaction
                        .getChargeResponsibility()
                        .getId(),

                transaction.getCharge() == null
                        ? null
                        : transaction.getCharge().getId(),

                transaction.getChargeLine() == null
                        ? null
                        : transaction
                        .getChargeLine()
                        .getId(),

                transaction.getPatientServiceProduct()
                        == null
                        ? null
                        : transaction
                        .getPatientServiceProduct()
                        .getId(),

                money(
                        transaction.getAmount()
                ),

                money(
                        transaction.getBalanceBefore()
                ),

                money(
                        transaction.getBalanceAfter()
                ),

                money(
                        account.getAvailableCredit()
                ),

                transaction.getCurrency(),

                transaction.getStatus()
        );
    }

    private BillingDebitSettlementResult buildSettlementResult(
            BillingDebitTransaction transaction
    ) {
        BillingDebitAccount account =
                transaction.getDebitAccount();

        return new BillingDebitSettlementResult(
                account.getId(),

                transaction.getId(),

                transaction.getTransactionNumber(),

                money(
                        transaction.getAmount()
                ),

                money(
                        transaction.getBalanceBefore()
                ),

                money(
                        transaction.getBalanceAfter()
                ),

                money(
                        account.getAvailableCredit()
                ),

                transaction.getStatus()
        );
    }

    private BigDecimal positiveMoney(
            BigDecimal value,
            String fieldName
    ) {
        BigDecimal amount =
                money(value);

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    fieldName
                            + " must be greater than zero.",
                    ENTITY_NAME,
                    "amount.invalid"
            );
        }

        return amount;
    }

    private BigDecimal nonNegativeMoney(
            BigDecimal value,
            String fieldName
    ) {
        BigDecimal amount =
                money(value);

        if (amount.signum() < 0) {
            throw new BadRequestAlertException(
                    fieldName
                            + " cannot be negative.",
                    ENTITY_NAME,
                    "amount.negative"
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
                    || normalized.compareTo(
                    result
            ) < 0) {
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

    private String generateAccountNumber() {
        return "DBA-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private String generateTransactionNumber() {
        return "DBT-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
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

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingDebitReversalResult reverseDebit(
            Long debitTransactionId,
            BigDecimal requestedAmount,
            String reason,
            String reversedBy,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateDebitReversalInput(
                debitTransactionId,
                requestedAmount,
                reason,
                reversedBy,
                requestId,
                sourceChannel
        );

        String idempotencyKey =
                "DEBIT:REVERSAL:TRANSACTION:"
                        + debitTransactionId
                        + ":"
                        + requestId.trim();

        BillingDebitTransaction existingReversal =
                billingDebitTransactionRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existingReversal != null) {
            return buildDebitReversalResult(
                    existingReversal,
                    null
            );
        }

        BillingDebitTransaction originalTransaction =
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

        validateOriginalDebitTransaction(
                originalTransaction
        );

        BillingAllocation allocation =
                billingAllocationRepository
                        .findFirstByDebitTransactionIdAndStatusInOrderByIdDesc(
                                originalTransaction.getId(),
                                EnumSet.of(
                                        BillingAllocationStatus.ACTIVE,
                                        BillingAllocationStatus.PARTIALLY_REVERSED
                                )
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Active debit allocation was not found for debit transaction "
                                                + originalTransaction.getId(),
                                        ENTITY_NAME,
                                        "debitAllocation.notfound"
                                )
                        );

        BillingDebitAccount account =
                lockAccount(
                        originalTransaction
                                .getDebitAccount()
                                .getId()
                );

        validateAccountUsable(account);

        BigDecimal amount =
                resolveDebitReversalAmount(
                        originalTransaction,
                        allocation,
                        requestedAmount
                );

        BigDecimal balanceBefore =
                money(
                        account.getCurrentDebitBalance()
                );

        if (balanceBefore.compareTo(amount) < 0) {
            throw new BadRequestAlertException(
                    "Debit reversal amount exceeds the current debit-account balance.",
                    ENTITY_NAME,
                    "debitReversal.exceedsBalance"
            );
        }

        /*
         * Reverse Charge / Responsibility / Allocation first.
         * No wallet operation is performed.
         */
        BillingAllocationReversalResult allocationResult =
                billingAllocationService
                        .reverseDebitAllocationBalances(
                                allocation.getId(),
                                amount,
                                reason,
                                reversedBy
                        );

        BigDecimal balanceAfter =
                balanceBefore.subtract(amount);

        BigDecimal availableCreditAfter =
                money(
                        account.getCreditLimit()
                ).subtract(balanceAfter);

        account.setCurrentDebitBalance(
                balanceAfter
        );

        account.setAvailableCredit(
                availableCreditAfter
        );

        /*
         * A reversal reduces the net debit-created history.
         * We do not reduce totalDebitCreated because it is an audit total.
         *
         * Keep:
         * totalDebitCreated = lifetime created amount
         * totalDebitSettled = actual payment settlements only
         */

        validateAccountBalance(account);

        BillingDebitAccount savedAccount =
                billingDebitAccountRepository.save(
                        account
                );

        BillingDebitTransaction reversalTransaction =
                BillingDebitTransaction.builder()
                        .transactionNumber(
                                generateTransactionNumber()
                        )
                        .debitAccount(
                                savedAccount
                        )
                        .patient(
                                originalTransaction.getPatient()
                        )
                        .encounter(
                                originalTransaction.getEncounter()
                        )
                        .chargeResponsibility(
                                originalTransaction
                                        .getChargeResponsibility()
                        )
                        .charge(
                                originalTransaction.getCharge()
                        )
                        .chargeLine(
                                originalTransaction.getChargeLine()
                        )
                        .patientServiceProduct(
                                originalTransaction
                                        .getPatientServiceProduct()
                        )
                        .payment(null)
                        .paymentTransaction(null)
                        .parentTransaction(
                                originalTransaction
                        )
                        .transactionType(
                                BillingDebitTransactionType
                                        .DEBIT_REVERSAL
                        )
                        .amount(amount)
                        .currency(
                                originalTransaction.getCurrency()
                        )
                        .balanceBefore(
                                balanceBefore
                        )
                        .balanceAfter(
                                balanceAfter
                        )
                        .status(
                                BillingDebitTransactionStatus.COMPLETED
                        )
                        .transactionDate(
                                Instant.now()
                        )
                        .dueDate(null)
                        .settledDate(null)
                        .referenceType(
                                "BILLING_DEBIT_TRANSACTION"
                        )
                        .referenceId(
                                originalTransaction.getId()
                        )
                        .referenceNumber(
                                originalTransaction
                                        .getTransactionNumber()
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
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                allocation
                                        .getTransactionGroupId()
                        )
                        .notes(
                                "Patient debit and related charge allocation reversed."
                        )
                        .build();

        BillingDebitTransaction savedReversal =
                saveTransaction(
                        reversalTransaction,
                        idempotencyKey
                );

        recordDebitReversalLedger(
                savedReversal,
                originalTransaction,
                allocation,
                amount,
                balanceBefore,
                balanceAfter,
                reason,
                requestId,
                sourceChannel
        );

        if (money(
                allocation.getRemainingAllocatedAmount()
        ).signum() == 0) {
            originalTransaction.setStatus(
                    BillingDebitTransactionStatus.REVERSED
            );

            billingDebitTransactionRepository.save(
                    originalTransaction
            );
        }

        LOG.info(
                "[REVERSE_DEBIT] Debit reversed "
                        + "originalTransactionId={} "
                        + "reversalTransactionId={} "
                        + "allocationId={} amount={} "
                        + "balanceBefore={} balanceAfter={}",
                originalTransaction.getId(),
                savedReversal.getId(),
                allocation.getId(),
                amount,
                balanceBefore,
                balanceAfter
        );

        return buildDebitReversalResult(
                savedReversal,
                allocationResult
        );
    }

    private BigDecimal resolveDebitReversalAmount(
            BillingDebitTransaction originalTransaction,
            BillingAllocation allocation,
            BigDecimal requestedAmount
    ) {
        BigDecimal requested =
                positiveMoney(
                        requestedAmount,
                        "Debit reversal amount"
                );

        BigDecimal originalAmount =
                money(
                        originalTransaction.getAmount()
                );

        BigDecimal allocationRemaining =
                money(
                        allocation
                                .getRemainingAllocatedAmount()
                );

        BigDecimal previouslyReversed =
                billingDebitTransactionRepository
                        .findAllByDebitAccount_IdOrderByTransactionDateAscIdAsc(
                                originalTransaction
                                        .getDebitAccount()
                                        .getId()
                        )
                        .stream()
                        .filter(transaction ->
                                transaction.getParentTransaction() != null
                                        && transaction
                                        .getParentTransaction()
                                        .getId()
                                        .equals(
                                                originalTransaction.getId()
                                        )
                        )
                        .filter(transaction ->
                                transaction.getTransactionType()
                                        == BillingDebitTransactionType
                                        .DEBIT_REVERSAL
                        )
                        .filter(transaction ->
                                transaction.getStatus()
                                        == BillingDebitTransactionStatus
                                        .COMPLETED
                        )
                        .map(
                                BillingDebitTransaction::getAmount
                        )
                        .map(this::money)
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        BigDecimal transactionRemaining =
                originalAmount
                        .subtract(previouslyReversed)
                        .max(zero());

        BigDecimal amount =
                minimum(
                        requested,
                        transactionRemaining,
                        allocationRemaining
                );

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "No debit amount remains available for reversal.",
                    ENTITY_NAME,
                    "debitReversal.amount.zero"
            );
        }

        return amount;
    }

    private void validateOriginalDebitTransaction(
            BillingDebitTransaction transaction
    ) {
        if (transaction.getTransactionType()
                != BillingDebitTransactionType.DEBIT_CREATED) {
            throw new BadRequestAlertException(
                    "Only DEBIT_CREATED transactions may be reversed.",
                    ENTITY_NAME,
                    "debitTransaction.type.notReversible"
            );
        }

        if (transaction.getStatus()
                != BillingDebitTransactionStatus.COMPLETED) {
            throw new BadRequestAlertException(
                    "Only completed debit transactions may be reversed.",
                    ENTITY_NAME,
                    "debitTransaction.status.notReversible"
            );
        }

        if (transaction.getDebitAccount() == null
                || transaction.getDebitAccount().getId() == null) {
            throw new BadRequestAlertException(
                    "Debit transaction does not have a debit account.",
                    ENTITY_NAME,
                    "debitTransaction.account.missing"
            );
        }

        if (transaction.getChargeResponsibility() == null
                || transaction
                .getChargeResponsibility()
                .getId() == null) {
            throw new BadRequestAlertException(
                    "Debit transaction does not have a billing responsibility.",
                    ENTITY_NAME,
                    "debitTransaction.responsibility.missing"
            );
        }
    }

    private void recordDebitReversalLedger(
            BillingDebitTransaction reversalTransaction,
            BillingDebitTransaction originalTransaction,
            BillingAllocation allocation,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String reason,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        reversalTransaction
                                .getTransactionGroupId(),

                        requestId.trim(),

                        reversalTransaction
                                .getIdempotencyKey()
                                + ":LEDGER",

                        reversalTransaction.getPatient(),

                        reversalTransaction.getEncounter(),

                        null,

                        null,

                        null,

                        reversalTransaction.getCharge(),

                        reversalTransaction.getChargeLine(),

                        reversalTransaction
                                .getChargeResponsibility(),

                        null,

                        allocation,

                        reversalTransaction
                                .getDebitAccount(),

                        reversalTransaction,

                        null,

                        BillingLedgerTransactionType
                                .DEBIT_REVERSED,

                        BillingLedgerScope
                                .DEBIT_ACCOUNT,

                        amount,

                        reversalTransaction.getCurrency(),

                        zero(),
                        zero(),
                        zero(),
                        zero(),

                        amount.negate(),

                        amount.negate(),

                        amount,

                        null,
                        null,
                        null,
                        null,

                        balanceBefore,
                        balanceAfter,

                        /*
                         * Responsibility was reopened by
                         * reverseDebitAllocationBalances().
                         */
                        money(
                                reversalTransaction
                                        .getChargeResponsibility()
                                        .getOutstandingAmount()
                        ).subtract(amount),

                        money(
                                reversalTransaction
                                        .getChargeResponsibility()
                                        .getOutstandingAmount()
                        ),

                        BillingLedgerEntryDirection.CREDIT,

                        BillingLedgerEntryCategory.REVERSAL,

                        null,

                        "BILLING_DEBIT_TRANSACTION",

                        originalTransaction.getId(),

                        originalTransaction
                                .getTransactionNumber(),

                        "Patient debit and related charge allocation reversed.",

                        reason.trim(),

                        sourceChannel
                )
        );
    }

    private BillingDebitReversalResult
    buildDebitReversalResult(
            BillingDebitTransaction reversalTransaction,
            BillingAllocationReversalResult allocationResult
    ) {
        BillingDebitAccount account =
                reversalTransaction.getDebitAccount();

        BillingChargeResponsibility responsibility =
                reversalTransaction
                        .getChargeResponsibility();

        return new BillingDebitReversalResult(
                reversalTransaction.getParentTransaction()
                        == null
                        ? null
                        : reversalTransaction
                        .getParentTransaction()
                        .getId(),

                reversalTransaction.getId(),

                reversalTransaction
                        .getTransactionNumber(),

                allocationResult == null
                        ? null
                        : allocationResult.allocationId(),

                money(
                        reversalTransaction.getAmount()
                ),

                money(
                        reversalTransaction.getBalanceBefore()
                ),

                money(
                        reversalTransaction.getBalanceAfter()
                ),

                money(
                        account.getAvailableCredit()
                ),

                responsibility == null
                        ? zero()
                        : money(
                        responsibility
                                .getOutstandingAmount()
                ),

                reversalTransaction.getChargeLine() == null
                        ? zero()
                        : money(
                        reversalTransaction
                                .getChargeLine()
                                .getOutstandingAmount()
                ),

                reversalTransaction.getCharge() == null
                        ? zero()
                        : money(
                        reversalTransaction
                                .getCharge()
                                .getOutstandingAmount()
                ),

                reversalTransaction.getCurrency(),

                reversalTransaction.getStatus(),

                allocationResult == null
                        ? null
                        : allocationResult.allocationStatus()
        );
    }

    private void validateDebitReversalInput(
            Long debitTransactionId,
            BigDecimal requestedAmount,
            String reason,
            String reversedBy,
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
                requestedAmount,
                "Debit reversal amount"
        );

        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Debit reversal reason is required.",
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

        validateRequestInfo(
                requestId,
                sourceChannel
        );
    }
}