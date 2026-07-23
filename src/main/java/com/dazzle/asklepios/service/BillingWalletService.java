package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingWalletStatus;
import com.dazzle.asklepios.repository.BillingWalletRepository;
import com.dazzle.asklepios.repository.PatientRepository;
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

@Service
@RequiredArgsConstructor
public class BillingWalletService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingWalletService.class
            );

    private static final String ENTITY_NAME =
            "billingWallet";

    private static final int MONEY_SCALE = 4;

    private final BillingWalletRepository
            billingWalletRepository;

    private final PatientRepository
            patientRepository;

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet lockWallet(
            Long patientId,
            Currency currency
    ) {
        validatePatientAndCurrency(
                patientId,
                currency
        );

        BillingWallet wallet =
                billingWalletRepository
                        .findByPatient_IdAndCurrency(
                                patientId,
                                currency
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing wallet not found for patient "
                                                + patientId
                                                + " and currency "
                                                + currency,
                                        ENTITY_NAME,
                                        "wallet.notfound"
                                )
                        );

        validateWalletUsable(wallet);

        return wallet;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet getOrCreateWallet(
            Long patientId,
            Currency currency
    ) {
        validatePatientAndCurrency(
                patientId,
                currency
        );

        BillingWallet existing =
                billingWalletRepository
                        .findByPatient_IdAndCurrency(
                                patientId,
                                currency
                        )
                        .orElse(null);

        if (existing != null) {
            validateWalletBalance(existing);
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

        BillingWallet wallet =
                BillingWallet.builder()
                        .patient(patient)
                        .currency(currency)
                        .creditedAmount(zero())
                        .availableBalance(zero())
                        .reservedBalance(zero())
                        .consumedAmount(zero())
                        .refundedAmount(zero())
                        .status(BillingWalletStatus.ACTIVE)
                        .build();

        try {
            BillingWallet saved =
                    billingWalletRepository
                            .saveAndFlush(wallet);

            LOG.info(
                    "[CREATE] Billing wallet created walletId={} patientId={} currency={}",
                    saved.getId(),
                    patientId,
                    currency
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingWallet concurrentWallet =
                    billingWalletRepository
                            .findByPatient_IdAndCurrency(
                                    patientId,
                                    currency
                            )
                            .orElse(null);

            if (concurrentWallet != null) {
                return concurrentWallet;
            }

            throw new BadRequestAlertException(
                    "Unable to create billing wallet.",
                    ENTITY_NAME,
                    "wallet.create.failed"
            );
        }
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet credit(
            Long patientId,
            Currency currency,
            BigDecimal amount
    ) {
        BigDecimal creditAmount =
                requirePositiveAmount(
                        amount,
                        "Credit amount"
                );

        BillingWallet wallet =
                getOrCreateWallet(
                        patientId,
                        currency
                );

        wallet = reloadAndLock(wallet);

        wallet.setCreditedAmount(
                money(wallet.getCreditedAmount())
                        .add(creditAmount)
        );

        wallet.setAvailableBalance(
                money(wallet.getAvailableBalance())
                        .add(creditAmount)
        );

        validateWalletBalance(wallet);

        BillingWallet saved =
                billingWalletRepository.save(wallet);

        LOG.info(
                "[CREDIT] Wallet credited walletId={} amount={} available={} reserved={}",
                saved.getId(),
                creditAmount,
                saved.getAvailableBalance(),
                saved.getReservedBalance()
        );

        return saved;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet reserve(
            BillingWallet wallet,
            BigDecimal requestedAmount
    ) {
        BillingWallet lockedWallet =
                reloadAndLock(wallet);

        BigDecimal amount =
                requirePositiveAmount(
                        requestedAmount,
                        "Reservation amount"
                );

        BigDecimal available =
                money(
                        lockedWallet.getAvailableBalance()
                );

        if (available.compareTo(amount) < 0) {
            throw new BadRequestAlertException(
                    "Insufficient available wallet balance. "
                            + "Available: "
                            + available
                            + ", requested: "
                            + amount,
                    ENTITY_NAME,
                    "wallet.insufficientAvailableBalance"
            );
        }

        lockedWallet.setAvailableBalance(
                available.subtract(amount)
        );

        lockedWallet.setReservedBalance(
                money(
                        lockedWallet.getReservedBalance()
                ).add(amount)
        );

        validateWalletBalance(lockedWallet);

        BillingWallet saved =
                billingWalletRepository.save(
                        lockedWallet
                );

        LOG.info(
                "[RESERVE] Wallet amount reserved walletId={} amount={} available={} reserved={}",
                saved.getId(),
                amount,
                saved.getAvailableBalance(),
                saved.getReservedBalance()
        );

        return saved;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet release(
            BillingWallet wallet,
            BigDecimal requestedAmount
    ) {
        BillingWallet lockedWallet =
                reloadAndLock(wallet);

        BigDecimal amount =
                requirePositiveAmount(
                        requestedAmount,
                        "Release amount"
                );

        BigDecimal reserved =
                money(
                        lockedWallet.getReservedBalance()
                );

        if (reserved.compareTo(amount) < 0) {
            throw new BadRequestAlertException(
                    "Release amount exceeds wallet reserved balance.",
                    ENTITY_NAME,
                    "wallet.release.exceedsReserved"
            );
        }

        lockedWallet.setReservedBalance(
                reserved.subtract(amount)
        );

        lockedWallet.setAvailableBalance(
                money(
                        lockedWallet.getAvailableBalance()
                ).add(amount)
        );

        validateWalletBalance(lockedWallet);

        BillingWallet saved =
                billingWalletRepository.save(
                        lockedWallet
                );

        LOG.info(
                "[RELEASE] Wallet reservation released walletId={} amount={} available={} reserved={}",
                saved.getId(),
                amount,
                saved.getAvailableBalance(),
                saved.getReservedBalance()
        );

        return saved;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet consumeReserved(
            BillingWallet wallet,
            BigDecimal requestedAmount
    ) {
        BillingWallet lockedWallet =
                reloadAndLock(wallet);

        BigDecimal amount =
                requirePositiveAmount(
                        requestedAmount,
                        "Reserved consumption amount"
                );

        BigDecimal reserved =
                money(
                        lockedWallet.getReservedBalance()
                );

        if (reserved.compareTo(amount) < 0) {
            throw new BadRequestAlertException(
                    "Consumption amount exceeds wallet reserved balance.",
                    ENTITY_NAME,
                    "wallet.consume.exceedsReserved"
            );
        }

        lockedWallet.setReservedBalance(
                reserved.subtract(amount)
        );

        lockedWallet.setConsumedAmount(
                money(
                        lockedWallet.getConsumedAmount()
                ).add(amount)
        );

        validateWalletBalance(lockedWallet);

        BillingWallet saved =
                billingWalletRepository.save(
                        lockedWallet
                );

        LOG.info(
                "[CONSUME_RESERVED] Reserved balance consumed walletId={} amount={} reserved={} consumed={}",
                saved.getId(),
                amount,
                saved.getReservedBalance(),
                saved.getConsumedAmount()
        );

        return saved;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet consumeAvailable(
            BillingWallet wallet,
            BigDecimal requestedAmount
    ) {
        BillingWallet lockedWallet =
                reloadAndLock(wallet);

        BigDecimal amount =
                requirePositiveAmount(
                        requestedAmount,
                        "Available consumption amount"
                );

        BigDecimal available =
                money(
                        lockedWallet.getAvailableBalance()
                );

        if (available.compareTo(amount) < 0) {
            throw new BadRequestAlertException(
                    "Consumption amount exceeds available wallet balance.",
                    ENTITY_NAME,
                    "wallet.consume.exceedsAvailable"
            );
        }

        lockedWallet.setAvailableBalance(
                available.subtract(amount)
        );

        lockedWallet.setConsumedAmount(
                money(
                        lockedWallet.getConsumedAmount()
                ).add(amount)
        );

        validateWalletBalance(lockedWallet);

        BillingWallet saved =
                billingWalletRepository.save(
                        lockedWallet
                );

        LOG.info(
                "[CONSUME_AVAILABLE] Available balance consumed walletId={} amount={} available={} consumed={}",
                saved.getId(),
                amount,
                saved.getAvailableBalance(),
                saved.getConsumedAmount()
        );

        return saved;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet refundAvailable(
            BillingWallet wallet,
            BigDecimal requestedAmount
    ) {
        BillingWallet lockedWallet =
                reloadAndLock(wallet);

        BigDecimal amount =
                requirePositiveAmount(
                        requestedAmount,
                        "Refund amount"
                );

        BigDecimal available =
                money(
                        lockedWallet.getAvailableBalance()
                );

        if (available.compareTo(amount) < 0) {
            throw new BadRequestAlertException(
                    "Refund amount exceeds available wallet balance. "
                            + "Reserved balance cannot be refunded.",
                    ENTITY_NAME,
                    "wallet.refund.exceedsAvailable"
            );
        }

        lockedWallet.setAvailableBalance(
                available.subtract(amount)
        );

        lockedWallet.setRefundedAmount(
                money(
                        lockedWallet.getRefundedAmount()
                ).add(amount)
        );

        validateWalletBalance(lockedWallet);

        BillingWallet saved =
                billingWalletRepository.save(
                        lockedWallet
                );

        LOG.info(
                "[REFUND] Available wallet balance refunded walletId={} amount={} available={} refunded={}",
                saved.getId(),
                amount,
                saved.getAvailableBalance(),
                saved.getRefundedAmount()
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public BillingWallet findByPatientAndCurrency(
            Long patientId,
            Currency currency
    ) {
        validatePatientAndCurrency(
                patientId,
                currency
        );

        return billingWalletRepository
                .findFirstByPatient_IdAndCurrencyOrderByIdAsc(
                        patientId,
                        currency
                )
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Billing wallet not found.",
                                ENTITY_NAME,
                                "wallet.notfound"
                        )
                );
    }

    private BillingWallet reloadAndLock(
            BillingWallet wallet
    ) {
        if (wallet == null
                || wallet.getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted billing wallet is required.",
                    ENTITY_NAME,
                    "wallet.required"
            );
        }

        BillingWallet lockedWallet =
                billingWalletRepository
                        .findById(wallet.getId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing wallet not found with id "
                                                + wallet.getId(),
                                        ENTITY_NAME,
                                        "wallet.notfound"
                                )
                        );

        validateWalletUsable(lockedWallet);

        return lockedWallet;
    }

    private void validateWalletUsable(
            BillingWallet wallet
    ) {
        if (wallet.getStatus()
                != BillingWalletStatus.ACTIVE) {
            throw new BadRequestAlertException(
                    "Billing wallet is not active.",
                    ENTITY_NAME,
                    "wallet.notActive"
            );
        }

        validateWalletBalance(wallet);
    }

    private void validateWalletBalance(
            BillingWallet wallet
    ) {
        BigDecimal credited =
                money(wallet.getCreditedAmount());

        BigDecimal available =
                money(wallet.getAvailableBalance());

        BigDecimal reserved =
                money(wallet.getReservedBalance());

        BigDecimal consumed =
                money(wallet.getConsumedAmount());

        BigDecimal refunded =
                money(wallet.getRefundedAmount());

        validateNonNegative(
                credited,
                "creditedAmount"
        );

        validateNonNegative(
                available,
                "availableBalance"
        );

        validateNonNegative(
                reserved,
                "reservedBalance"
        );

        validateNonNegative(
                consumed,
                "consumedAmount"
        );

        validateNonNegative(
                refunded,
                "refundedAmount"
        );

        BigDecimal distributed =
                available
                        .add(reserved)
                        .add(consumed)
                        .add(refunded);

        if (credited.compareTo(distributed) != 0) {
            throw new BadRequestAlertException(
                    "Wallet balance is inconsistent.",
                    ENTITY_NAME,
                    "wallet.balance.invalid"
            );
        }

        wallet.setCreditedAmount(credited);
        wallet.setAvailableBalance(available);
        wallet.setReservedBalance(reserved);
        wallet.setConsumedAmount(consumed);
        wallet.setRefundedAmount(refunded);
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

    private BigDecimal requirePositiveAmount(
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

    private void validateNonNegative(
            BigDecimal value,
            String fieldName
    ) {
        if (value.signum() < 0) {
            throw new BadRequestAlertException(
                    fieldName
                            + " cannot be negative.",
                    ENTITY_NAME,
                    fieldName + ".negative"
            );
        }
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

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingWallet reverseConsumedToAvailable(
            BillingWallet wallet,
            BigDecimal requestedAmount
    ) {
        BillingWallet lockedWallet =
                reloadAndLock(wallet);

        BigDecimal amount =
                requirePositiveAmount(
                        requestedAmount,
                        "Consumed reversal amount"
                );

        BigDecimal consumed =
                money(
                        lockedWallet.getConsumedAmount()
                );

        if (consumed.compareTo(amount) < 0) {
            throw new BadRequestAlertException(
                    "Reversal amount exceeds wallet consumed balance.",
                    ENTITY_NAME,
                    "wallet.reversal.exceedsConsumed"
            );
        }

        lockedWallet.setConsumedAmount(
                consumed.subtract(amount)
        );

        lockedWallet.setAvailableBalance(
                money(
                        lockedWallet.getAvailableBalance()
                ).add(amount)
        );

        validateWalletBalance(
                lockedWallet
        );

        BillingWallet saved =
                billingWalletRepository.save(
                        lockedWallet
                );

        LOG.info(
                "[REVERSE_CONSUMED] Consumed wallet balance reversed "
                        + "walletId={} amount={} available={} consumed={}",
                saved.getId(),
                amount,
                saved.getAvailableBalance(),
                saved.getConsumedAmount()
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public BillingWallet findOptionalByPatientAndCurrency(
            Long patientId,
            Currency currency
    ) {
        if (patientId == null || currency == null) {
            return null;
        }

        return billingWalletRepository
                .findFirstByPatient_IdAndCurrencyOrderByIdAsc(
                        patientId,
                        currency
                )
                .orElse(null);
    }
}