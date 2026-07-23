package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingLedger;
import com.dazzle.asklepios.repository.BillingLedgerRepository;
import com.dazzle.asklepios.service.dto.billing.BillingLedgerEntryRequest;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingLedgerService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingLedgerService.class
            );

    private static final String ENTITY_NAME =
            "billingLedger";

    private static final int MONEY_SCALE = 4;

    private final BillingLedgerRepository
            billingLedgerRepository;

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingLedger record(
            BillingLedgerEntryRequest request
    ) {
        validateRequest(request);

        BillingLedger existing =
                billingLedgerRepository
                        .findByIdempotencyKey(
                                request.idempotencyKey()
                        )
                        .orElse(null);

        if (existing != null) {
            return existing;
        }

        BillingLedger ledger =
                BillingLedger.builder()
                        .ledgerNumber(
                                generateLedgerNumber()
                        )
                        .transactionGroupId(
                                request.transactionGroupId()
                        )
                        .correlationId(
                                trimToNull(
                                        request.correlationId()
                                )
                        )
                        .idempotencyKey(
                                request.idempotencyKey()
                        )

                        .patient(request.patient())
                        .encounter(request.encounter())

                        .wallet(request.wallet())
                        .payment(request.payment())
                        .paymentTransaction(
                                request.paymentTransaction()
                        )

                        .charge(request.charge())
                        .chargeLine(request.chargeLine())
                        .chargeResponsibility(
                                request.chargeResponsibility()
                        )

                        .reservation(request.reservation())
                        .allocation(request.allocation())

                        .debitAccount(
                                request.debitAccount()
                        )
                        .debitTransaction(
                                request.debitTransaction()
                        )
                        .refund(request.refund())

                        .transactionType(
                                request.transactionType()
                        )
                        .ledgerScope(
                                request.ledgerScope()
                        )

                        .amount(
                                positiveMoney(
                                        request.amount()
                                )
                        )
                        .currency(request.currency())

                        .availableChange(
                                money(
                                        request.availableChange()
                                )
                        )
                        .reservedChange(
                                money(
                                        request.reservedChange()
                                )
                        )
                        .consumedChange(
                                money(
                                        request.consumedChange()
                                )
                        )
                        .refundedChange(
                                money(
                                        request.refundedChange()
                                )
                        )
                        .debitBalanceChange(
                                money(
                                        request.debitBalanceChange()
                                )
                        )
                        .allocatedChange(
                                money(
                                        request.allocatedChange()
                                )
                        )
                        .outstandingChange(
                                money(
                                        request.outstandingChange()
                                )
                        )

                        .walletAvailableBefore(
                                nullableMoney(
                                        request.walletAvailableBefore()
                                )
                        )
                        .walletAvailableAfter(
                                nullableMoney(
                                        request.walletAvailableAfter()
                                )
                        )
                        .walletReservedBefore(
                                nullableMoney(
                                        request.walletReservedBefore()
                                )
                        )
                        .walletReservedAfter(
                                nullableMoney(
                                        request.walletReservedAfter()
                                )
                        )

                        .debitBalanceBefore(
                                nullableMoney(
                                        request.debitBalanceBefore()
                                )
                        )
                        .debitBalanceAfter(
                                nullableMoney(
                                        request.debitBalanceAfter()
                                )
                        )

                        .responsibilityOutstandingBefore(
                                nullableMoney(
                                        request
                                                .responsibilityOutstandingBefore()
                                )
                        )
                        .responsibilityOutstandingAfter(
                                nullableMoney(
                                        request
                                                .responsibilityOutstandingAfter()
                                )
                        )

                        .entryDirection(
                                request.entryDirection()
                        )
                        .entryCategory(
                                request.entryCategory()
                        )

                        .reversedLedger(
                                request.reversedLedger()
                        )

                        .referenceType(
                                trimToNull(
                                        request.referenceType()
                                )
                        )
                        .referenceId(
                                request.referenceId()
                        )
                        .referenceNumber(
                                trimToNull(
                                        request.referenceNumber()
                                )
                        )

                        .description(
                                trimToNull(
                                        request.description()
                                )
                        )
                        .reason(
                                trimToNull(
                                        request.reason()
                                )
                        )

                        .transactionDate(
                                Instant.now()
                        )
                        .sourceChannel(
                                request.sourceChannel()
                        )
                        .build();

        try {
            BillingLedger saved =
                    billingLedgerRepository
                            .saveAndFlush(ledger);

            LOG.info(
                    "[RECORD] Ledger created ledgerId={} "
                            + "ledgerNumber={} transactionType={} "
                            + "scope={} amount={}",
                    saved.getId(),
                    saved.getLedgerNumber(),
                    saved.getTransactionType(),
                    saved.getLedgerScope(),
                    saved.getAmount()
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingLedger concurrent =
                    billingLedgerRepository
                            .findByIdempotencyKey(
                                    request.idempotencyKey()
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            LOG.error(
                    "[RECORD] Ledger creation failed "
                            + "idempotencyKey={} type={} amount={}",
                    request.idempotencyKey(),
                    request.transactionType(),
                    request.amount(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to record billing ledger entry.",
                    ENTITY_NAME,
                    "ledger.create.failed"
            );
        }
    }

    private void validateRequest(
            BillingLedgerEntryRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Ledger request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.transactionGroupId() == null) {
            throw new BadRequestAlertException(
                    "Transaction group ID is required.",
                    ENTITY_NAME,
                    "transactionGroupId.required"
            );
        }

        if (request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()) {
            throw new BadRequestAlertException(
                    "Ledger idempotency key is required.",
                    ENTITY_NAME,
                    "idempotencyKey.required"
            );
        }

        if (request.patient() == null
                || request.patient().getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted patient is required.",
                    ENTITY_NAME,
                    "patient.required"
            );
        }

        if (request.transactionType() == null) {
            throw new BadRequestAlertException(
                    "Ledger transaction type is required.",
                    ENTITY_NAME,
                    "transactionType.required"
            );
        }

        if (request.ledgerScope() == null) {
            throw new BadRequestAlertException(
                    "Ledger scope is required.",
                    ENTITY_NAME,
                    "ledgerScope.required"
            );
        }

        if (request.currency() == null) {
            throw new BadRequestAlertException(
                    "Ledger currency is required.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        if (request.entryDirection() == null) {
            throw new BadRequestAlertException(
                    "Ledger entry direction is required.",
                    ENTITY_NAME,
                    "entryDirection.required"
            );
        }

        if (request.entryCategory() == null) {
            throw new BadRequestAlertException(
                    "Ledger entry category is required.",
                    ENTITY_NAME,
                    "entryCategory.required"
            );
        }

        if (request.sourceChannel() == null) {
            throw new BadRequestAlertException(
                    "Ledger source channel is required.",
                    ENTITY_NAME,
                    "sourceChannel.required"
            );
        }

        positiveMoney(request.amount());
    }

    private BigDecimal positiveMoney(
            BigDecimal value
    ) {
        BigDecimal amount = money(value);

        if (amount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Ledger amount must be greater than zero.",
                    ENTITY_NAME,
                    "amount.invalid"
            );
        }

        return amount;
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        )
                : value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal nullableMoney(
            BigDecimal value
    ) {
        return value == null
                ? null
                : money(value);
    }

    private String generateLedgerNumber() {
        return "LED-"
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
}