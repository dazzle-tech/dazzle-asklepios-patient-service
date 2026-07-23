package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingReservationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.BillingReservationRepository;
import com.dazzle.asklepios.service.dto.billing.BillingAllocationResult;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutLineResult;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutResult;
import com.dazzle.asklepios.service.dto.billing.BillingDebitCreationResult;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingCheckoutService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingCheckoutService.class
            );

    private static final String ENTITY_NAME =
            "billingCheckout";

    private static final int MONEY_SCALE = 4;

    private static final EnumSet<BillingResponsibilityStatus>
            EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED
            );

    private final BillingChargeRepository
            billingChargeRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final BillingReservationRepository
            billingReservationRepository;

    private final BillingAllocationService
            billingAllocationService;

    private final BillingDebitService
            billingDebitService;

    private final BillingWalletService
            billingWalletService;

    private final BillingChargeService
            billingChargeService;

    @Transactional(rollbackFor = Exception.class)
    public BillingCheckoutResult checkout(
            BillingCheckoutRequest request
    ) {
        validateRequest(request);

        BillingCharge charge =
                billingChargeRepository
                        .findById(request.chargeId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing charge not found with id "
                                                + request.chargeId(),
                                        ENTITY_NAME,
                                        "charge.notfound"
                                )
                        );

        validateChargeForCheckout(charge);

        LOG.info(
                "[CHECKOUT_START] Checkout started "
                        + "chargeId={} encounterId={} patientId={} "
                        + "requestId={}",
                charge.getId(),
                charge.getEncounter().getId(),
                charge.getPatient().getId(),
                request.requestId()
        );

        List<BillingChargeResponsibility>
                patientResponsibilities =
                billingChargeResponsibilityRepository
                        .findAllByCharge_IdAndResponsiblePartyTypeAndStatusNotInOrderByIdAsc(
                                charge.getId(),
                                ResponsiblePartyType.PATIENT,
                                EXCLUDED_RESPONSIBILITY_STATUSES
                        );

        List<BillingCheckoutLineResult> lineResults =
                new ArrayList<>();

        BigDecimal totalReservedAllocated = zero();
        BigDecimal totalAvailableAllocated = zero();
        BigDecimal totalDebitCreated = zero();

        for (BillingChargeResponsibility responsibility
                : patientResponsibilities) {

            BillingCheckoutLineResult lineResult =
                    settlePatientResponsibility(
                            responsibility,
                            request
                    );

            lineResults.add(lineResult);

            totalReservedAllocated =
                    totalReservedAllocated.add(
                            money(
                                    lineResult
                                            .reservedAllocationAmount()
                            )
                    );

            totalAvailableAllocated =
                    totalAvailableAllocated.add(
                            money(
                                    lineResult
                                            .availableWalletAllocationAmount()
                            )
                    );

            totalDebitCreated =
                    totalDebitCreated.add(
                            money(
                                    lineResult
                                            .debitAllocationAmount()
                            )
                    );
        }

        /*
         * Reload and validate all responsibilities after settlement.
         */
        List<BillingChargeResponsibility>
                finalResponsibilities =
                billingChargeResponsibilityRepository
                        .findAllByCharge_IdAndStatusNotInOrderByIdAsc(
                                charge.getId(),
                                EXCLUDED_RESPONSIBILITY_STATUSES
                        );

        BigDecimal patientOutstanding =
                sumOutstandingByType(
                        finalResponsibilities,
                        ResponsiblePartyType.PATIENT
                );

        BigDecimal insuranceOutstanding =
                sumOutstandingByType(
                        finalResponsibilities,
                        ResponsiblePartyType.INSURANCE
                );

        BigDecimal otherPayerOutstanding =
                sumOutstandingByType(
                        finalResponsibilities,
                        ResponsiblePartyType.OTHER_PAYER
                );

        if (patientOutstanding.signum() > 0) {
            throw new BadRequestAlertException(
                    "Checkout failed because patient responsibility "
                            + "still has an outstanding amount: "
                            + patientOutstanding,
                    ENTITY_NAME,
                    "patientOutstanding.remaining"
            );
        }

        /*
         * Recalculate charge totals and status from charge lines.
         */
        BillingProcessingContext context =
                BillingProcessingContext.builder()
                        .charge(charge)
                        .idempotencyKey(
                                "CHECKOUT:"
                                        + request.requestId()
                        )
                        .build();

        BillingCharge recalculatedCharge =
                billingChargeService
                        .recalculateChargeTotals(
                                context
                        );

        BigDecimal totalOutstanding =
                money(
                        recalculatedCharge
                                .getOutstandingAmount()
                );

        boolean financiallyClosed =
                totalOutstanding.signum() == 0;

        if (financiallyClosed) {
            recalculatedCharge.setStatus(
                    BillingChargeStatus.CLOSED
            );

            recalculatedCharge.setClosedDate(
                    Instant.now()
            );

            recalculatedCharge =
                    billingChargeRepository.save(
                            recalculatedCharge
                    );

        } else if (money(
                recalculatedCharge.getAllocatedAmount()
        ).signum() > 0) {
            recalculatedCharge.setStatus(
                    BillingChargeStatus
                            .PARTIALLY_ALLOCATED
            );

            recalculatedCharge =
                    billingChargeRepository.save(
                            recalculatedCharge
                    );
        }

        LOG.info(
                "[CHECKOUT_COMPLETE] Checkout completed "
                        + "chargeId={} reservedAllocated={} "
                        + "walletAllocated={} debitCreated={} "
                        + "patientOutstanding={} "
                        + "insuranceOutstanding={} "
                        + "totalOutstanding={} status={}",
                recalculatedCharge.getId(),
                totalReservedAllocated,
                totalAvailableAllocated,
                totalDebitCreated,
                patientOutstanding,
                insuranceOutstanding,
                totalOutstanding,
                recalculatedCharge.getStatus()
        );

        return new BillingCheckoutResult(
                recalculatedCharge.getId(),

                recalculatedCharge.getChargeNumber(),

                recalculatedCharge
                        .getPatient()
                        .getId(),

                recalculatedCharge
                        .getEncounter()
                        .getId(),

                money(
                        recalculatedCharge.getNetAmount()
                ),

                totalReservedAllocated,

                totalAvailableAllocated,

                totalDebitCreated,

                patientOutstanding,

                insuranceOutstanding,

                otherPayerOutstanding,

                totalOutstanding,

                recalculatedCharge.getCurrency(),

                recalculatedCharge.getStatus(),

                patientOutstanding.signum() == 0,

                financiallyClosed,

                lineResults
        );
    }

    private BillingCheckoutLineResult
    settlePatientResponsibility(
            BillingChargeResponsibility initialResponsibility,
            BillingCheckoutRequest request
    ) {
        BillingChargeResponsibility responsibility =
                reloadResponsibility(
                        initialResponsibility.getId()
                );

        BigDecimal originalAmount =
                money(
                        responsibility
                                .getResponsibilityAmount()
                );

        BigDecimal reservedAllocated = zero();
        BigDecimal availableAllocated = zero();
        BigDecimal debitAllocated = zero();

        /*
         * ========================================================
         * 1. Consume all active reservations for this responsibility.
         * ========================================================
         */
        List<BillingReservation> reservations =
                billingReservationRepository
                        .findAllByChargeResponsibility_IdAndStatusOrderByReservedDateAscIdAsc(
                                responsibility.getId(),
                                BillingReservationStatus.ACTIVE
                        );

        for (BillingReservation reservation : reservations) {
            responsibility =
                    reloadResponsibility(
                            responsibility.getId()
                    );

            BigDecimal outstanding =
                    money(
                            responsibility
                                    .getOutstandingAmount()
                    );

            if (outstanding.signum() == 0) {
                break;
            }

            BigDecimal reservationRemaining =
                    money(
                            reservation
                                    .getRemainingReservedAmount()
                    );

            BigDecimal amount =
                    minimum(
                            outstanding,
                            reservationRemaining
                    );

            if (amount.signum() <= 0) {
                continue;
            }

            BillingAllocationResult allocationResult =
                    billingAllocationService
                            .allocateFromReservation(
                                    reservation.getId(),
                                    amount,
                                    request.requestId()
                                            + ":RESP:"
                                            + responsibility.getId()
                                            + ":RESERVATION:"
                                            + reservation.getId(),
                                    request.sourceChannel()
                            );

            reservedAllocated =
                    reservedAllocated.add(
                            money(
                                    allocationResult
                                            .allocatedAmount()
                            )
                    );
        }

        responsibility =
                reloadResponsibility(
                        responsibility.getId()
                );

        /*
         * ========================================================
         * 2. Use available patient wallet balance.
         * ========================================================
         */
        BigDecimal outstandingAfterReservation =
                money(
                        responsibility.getOutstandingAmount()
                );

        if (outstandingAfterReservation.signum() > 0) {
            BillingWallet wallet =
                    billingWalletService
                            .findOptionalByPatientAndCurrency(
                                    responsibility
                                            .getPatient()
                                            .getId(),
                                    responsibility
                                            .getCurrency()
                            );

            BigDecimal available =
                    wallet == null
                            ? zero()
                            : money(
                            wallet.getAvailableBalance()
                    );

            BigDecimal amount =
                    minimum(
                            outstandingAfterReservation,
                            available
                    );

            if (amount.signum() > 0) {
                BillingAllocationResult allocationResult =
                        billingAllocationService
                                .allocateFromAvailableWallet(
                                        responsibility.getId(),
                                        amount,
                                        request.requestId()
                                                + ":RESP:"
                                                + responsibility.getId()
                                                + ":AVAILABLE_WALLET",
                                        request.sourceChannel()
                                );

                availableAllocated =
                        money(
                                allocationResult
                                        .allocatedAmount()
                        );
            }
        }

        responsibility =
                reloadResponsibility(
                        responsibility.getId()
                );

        /*
         * ========================================================
         * 3. Create patient debit for the remaining amount.
         * ========================================================
         */
        BigDecimal outstandingAfterWallet =
                money(
                        responsibility.getOutstandingAmount()
                );

        if (outstandingAfterWallet.signum() > 0
                && Boolean.TRUE.equals(
                request.allowDebit()
        )) {
            BillingDebitCreationResult debitResult =
                    billingDebitService.createDebit(
                            responsibility.getId(),

                            outstandingAfterWallet,

                            money(
                                    request.creditLimit()
                            ),

                            true,

                            Boolean.TRUE.equals(
                                    request
                                            .debitApprovalRequired()
                            ),

                            request.approvedBy(),

                            request.debitDueDate(),

                            "Debit created during billing checkout.",

                            request.requestId()
                                    + ":RESP:"
                                    + responsibility.getId()
                                    + ":DEBIT",

                            request.sourceChannel()
                    );

            BillingAllocationResult allocationResult =
                    billingAllocationService
                            .allocateFromDebitTransaction(
                                    debitResult
                                            .debitTransactionId(),

                                    debitResult
                                            .debitAmount(),

                                    request.requestId()
                                            + ":RESP:"
                                            + responsibility.getId()
                                            + ":DEBIT_ALLOCATION",

                                    request.sourceChannel()
                            );

            debitAllocated =
                    money(
                            allocationResult
                                    .allocatedAmount()
                    );
        }

        responsibility =
                reloadResponsibility(
                        responsibility.getId()
                );

        BigDecimal finalOutstanding =
                money(
                        responsibility
                                .getOutstandingAmount()
                );

        if (finalOutstanding.signum() > 0) {
            throw new BadRequestAlertException(
                    "Unable to settle patient responsibility "
                            + responsibility.getId()
                            + ". Remaining amount: "
                            + finalOutstanding,
                    ENTITY_NAME,
                    "responsibility.unsettled"
            );
        }

        return new BillingCheckoutLineResult(
                responsibility
                        .getChargeLine()
                        .getId(),

                responsibility
                        .getPatientServiceProduct()
                        .getId(),

                responsibility.getId(),

                originalAmount,

                reservedAllocated,

                availableAllocated,

                debitAllocated,

                finalOutstanding,

                true
        );
    }

    private BillingChargeResponsibility
    reloadResponsibility(
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

    private BigDecimal sumOutstandingByType(
            List<BillingChargeResponsibility> responsibilities,
            ResponsiblePartyType partyType
    ) {
        return responsibilities.stream()
                .filter(responsibility ->
                        responsibility
                                .getResponsiblePartyType()
                                == partyType
                )
                .map(
                        BillingChargeResponsibility::
                                getOutstandingAmount
                )
                .map(this::money)
                .reduce(
                        zero(),
                        BigDecimal::add
                );
    }

    private void validateChargeForCheckout(
            BillingCharge charge
    ) {
        if (charge.getPatient() == null
                || charge.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Charge patient is missing.",
                    ENTITY_NAME,
                    "charge.patient.missing"
            );
        }

        if (charge.getEncounter() == null
                || charge.getEncounter().getId() == null) {
            throw new BadRequestAlertException(
                    "Charge encounter is missing.",
                    ENTITY_NAME,
                    "charge.encounter.missing"
            );
        }

        if (charge.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Charge currency is missing.",
                    ENTITY_NAME,
                    "charge.currency.missing"
            );
        }

        if (charge.getStatus()
                == BillingChargeStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled charge cannot be checked out.",
                    ENTITY_NAME,
                    "charge.cancelled"
            );
        }

        if (charge.getStatus()
                == BillingChargeStatus.REVERSED) {
            throw new BadRequestAlertException(
                    "Reversed charge cannot be checked out.",
                    ENTITY_NAME,
                    "charge.reversed"
            );
        }

        if (charge.getStatus()
                == BillingChargeStatus.CLOSED) {
            throw new BadRequestAlertException(
                    "Charge is already financially closed.",
                    ENTITY_NAME,
                    "charge.alreadyClosed"
            );
        }
    }

    private void validateRequest(
            BillingCheckoutRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Checkout request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.chargeId() == null) {
            throw new BadRequestAlertException(
                    "Charge ID is required.",
                    ENTITY_NAME,
                    "chargeId.required"
            );
        }

        if (request.allowDebit() == null) {
            throw new BadRequestAlertException(
                    "Allow-debit option is required.",
                    ENTITY_NAME,
                    "allowDebit.required"
            );
        }

        if (Boolean.TRUE.equals(
                request.allowDebit()
        )) {
            if (request.creditLimit() == null
                    || request.creditLimit()
                    .signum() < 0) {
                throw new BadRequestAlertException(
                        "A non-negative credit limit is required when debit is allowed.",
                        ENTITY_NAME,
                        "creditLimit.invalid"
                );
            }

            if (Boolean.TRUE.equals(
                    request.debitApprovalRequired()
            )
                    && (request.approvedBy() == null
                    || request.approvedBy()
                    .isBlank())) {
                throw new BadRequestAlertException(
                        "Approved-by user is required when debit approval is required.",
                        ENTITY_NAME,
                        "approvedBy.required"
                );
            }
        }

        if (request.checkoutBy() == null
                || request.checkoutBy().isBlank()) {
            throw new BadRequestAlertException(
                    "Checkout-by user is required.",
                    ENTITY_NAME,
                    "checkoutBy.required"
            );
        }

        if (request.requestId() == null
                || request.requestId().isBlank()) {
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
}