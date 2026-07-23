package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingReservationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ReservationReleaseReason;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.BillingReservationRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
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
public class BillingReservationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingReservationService.class
            );

    private static final String ENTITY_NAME =
            "billingReservation";

    private static final int MONEY_SCALE = 4;

    private final BillingReservationRepository
            billingReservationRepository;

    private final BillingChargeLineRepository
            billingChargeLineRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final BillingWalletService
            billingWalletService;

    /**
     * Reserves patient responsibility from one specific payment source.
     *
     * Supports partial reservation:
     *
     * requested = 100
     * wallet available = 60
     * payment remaining = 40
     *
     * reservation created = 40
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingReservation reserve(
            BillingProcessingContext context,
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction
    ) {
        validateReservationContext(
                context,
                payment
        );

        BillingChargeResponsibility responsibility =
                context.getPatientResponsibility();

        if (responsibility == null) {
            LOG.debug(
                    "[RESERVE] No patient responsibility exists pspId={}",
                    context.getPatientServiceProduct().getId()
            );

            return null;
        }

        BigDecimal responsibilityOutstanding =
                money(
                        responsibility.getOutstandingAmount()
                );

        if (responsibilityOutstanding.signum() == 0) {
            return null;
        }

        String idempotencyKey =
                context.getIdempotencyKey()
                        + ":RESERVATION:PAYMENT:"
                        + payment.getId();

        BillingReservation existing =
                billingReservationRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            context.setReservation(existing);
            context.setReservedAmount(
                    money(
                            existing.getRemainingReservedAmount()
                    )
            );

            return existing;
        }

        BillingWallet wallet =
                billingWalletService.lockWallet(
                        context
                                .getPatientServiceProduct()
                                .getPatientId(),
                        context
                                .getPatientServiceProduct()
                                .getCurrency()
                );

        validatePaymentSource(
                payment,
                paymentTransaction,
                wallet,
                context
        );

        BigDecimal walletAvailable =
                money(wallet.getAvailableBalance());

        BigDecimal paymentAvailable =
                calculatePaymentReservableAmount(
                        payment
                );

        BigDecimal reservationAmount =
                minimum(
                        responsibilityOutstanding,
                        walletAvailable,
                        paymentAvailable
                );

        if (reservationAmount.signum() == 0) {
            LOG.info(
                    "[RESERVE] No reservable balance pspId={} paymentId={} "
                            + "responsibilityOutstanding={} walletAvailable={} paymentAvailable={}",
                    context.getPatientServiceProduct().getId(),
                    payment.getId(),
                    responsibilityOutstanding,
                    walletAvailable,
                    paymentAvailable
            );

            return null;
        }

        BillingWallet updatedWallet =
                billingWalletService.reserve(
                        wallet,
                        reservationAmount
                );

        BillingReservation reservation =
                BillingReservation.builder()
                        .reservationNumber(
                                generateReservationNumber()
                        )
                        .wallet(updatedWallet)
                        .payment(payment)
                        .paymentTransaction(
                                paymentTransaction
                        )
                        .patient(
                                context
                                        .getChargeLine()
                                        .getPatient()
                        )
                        .encounter(
                                context
                                        .getChargeLine()
                                        .getEncounter()
                        )
                        .charge(
                                context
                                        .getChargeLine()
                                        .getCharge()
                        )
                        .chargeLine(
                                context.getChargeLine()
                        )
                        .chargeResponsibility(
                                responsibility
                        )
                        .patientServiceProduct(
                                context
                                        .getPatientServiceProduct()
                        )
                        .originalReservedAmount(
                                reservationAmount
                        )
                        .remainingReservedAmount(
                                reservationAmount
                        )
                        .consumedAmount(
                                zero()
                        )
                        .releasedAmount(
                                zero()
                        )
                        .currency(
                                context
                                        .getChargeLine()
                                        .getCurrency()
                        )
                        .status(
                                BillingReservationStatus.ACTIVE
                        )
                        .reservedDate(
                                Instant.now()
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                context.getTransactionGroupId()
                                        == null
                                        ? UUID.randomUUID()
                                        : context.getTransactionGroupId()
                        )
                        .build();

        try {
            BillingReservation saved =
                    billingReservationRepository
                            .saveAndFlush(
                                    reservation
                            );

            updateReservedAmountsAfterCreation(
                    context,
                    saved
            );

            context.setReservation(saved);
            context.setReservedAmount(
                    calculateActiveReservedAmount(
                            context
                                    .getChargeLine()
                                    .getId()
                    )
            );

            LOG.info(
                    "[RESERVE] Reservation created "
                            + "reservationId={} reservationNumber={} "
                            + "pspId={} paymentId={} amount={}",
                    saved.getId(),
                    saved.getReservationNumber(),
                    context
                            .getPatientServiceProduct()
                            .getId(),
                    payment.getId(),
                    reservationAmount
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            LOG.error(
                    "[RESERVE] Reservation creation failed "
                            + "pspId={} paymentId={} amount={}",
                    context
                            .getPatientServiceProduct()
                            .getId(),
                    payment.getId(),
                    reservationAmount,
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to create billing reservation.",
                    ENTITY_NAME,
                    "reservation.create.failed"
            );
        }
    }

    /**
     * Reserves from several payment records in FIFO order.
     *
     * Use this when the wallet balance came from multiple advance
     * payments.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BigDecimal reserveFromPayments(
            BillingProcessingContext context,
            List<BillingPayment> payments
    ) {
        if (context == null
                || context.getPatientResponsibility() == null
                || context.getChargeLine() == null) {

            return zero();
        }

        if (payments == null
                || payments.isEmpty()) {
            context.setReservedAmount(
                    zero()
            );

            return zero();
        }

        BigDecimal required =
                money(
                        context
                                .getPatientResponsibility()
                                .getOutstandingAmount()
                );

        if (required.signum() <= 0) {
            context.setReservedAmount(
                    zero()
            );

            return zero();
        }

        BigDecimal alreadyReserved =
                calculateActiveReservedAmount(
                        context
                                .getChargeLine()
                                .getId()
                );

        BigDecimal remainingRequired =
                required.subtract(
                        alreadyReserved
                ).max(zero());

        if (remainingRequired.signum() <= 0) {
            context.setReservedAmount(
                    alreadyReserved
            );

            return alreadyReserved;
        }

        for (BillingPayment payment : payments) {
            if (remainingRequired.signum() <= 0) {
                break;
            }

            BigDecimal paymentAvailable =
                    calculatePaymentReservableAmount(
                            payment
                    );

            if (paymentAvailable.signum() <= 0) {
                continue;
            }

            /*
             * reserve() calculates using responsibility outstanding.
             * To prevent over-reservation across multiple payments,
             * temporarily limit the amount visible to this iteration.
             */
            BigDecimal reservationLimit =
                    minimum(
                            remainingRequired,
                            paymentAvailable
                    );

            BillingReservation reservation =
                    reserveLimited(
                            context,
                            payment,
                            null,
                            reservationLimit
                    );

            if (reservation != null) {
                remainingRequired =
                        remainingRequired.subtract(
                                money(
                                        reservation
                                                .getOriginalReservedAmount()
                                )
                        ).max(zero());
            }
        }

        BigDecimal totalReserved =
                calculateActiveReservedAmount(
                        context
                                .getChargeLine()
                                .getId()
                );

        context.setReservedAmount(
                totalReserved
        );

        return totalReserved;
    }

    private BillingReservation reserveLimited(
            BillingProcessingContext context,
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction,
            BigDecimal maximumAmount
    ) {
        validateReservationContext(
                context,
                payment
        );

        BigDecimal limit =
                money(maximumAmount);

        if (limit.signum() <= 0) {
            return null;
        }

        BillingChargeResponsibility responsibility =
                context.getPatientResponsibility();

        String idempotencyKey =
                context.getIdempotencyKey()
                        + ":RESERVATION:PAYMENT:"
                        + payment.getId();

        BillingReservation existing =
                billingReservationRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            context.setReservation(existing);
            return existing;
        }

        BillingWallet wallet =
                billingWalletService.lockWallet(
                        context
                                .getPatientServiceProduct()
                                .getPatientId(),
                        context
                                .getPatientServiceProduct()
                                .getCurrency()
                );

        validatePaymentSource(
                payment,
                paymentTransaction,
                wallet,
                context
        );

        BigDecimal reservationAmount =
                minimum(
                        limit,
                        money(
                                wallet.getAvailableBalance()
                        ),
                        calculatePaymentReservableAmount(
                                payment
                        )
                );

        if (reservationAmount.signum() <= 0) {
            return null;
        }

        BillingWallet updatedWallet =
                billingWalletService.reserve(
                        wallet,
                        reservationAmount
                );

        BillingReservation reservation =
                BillingReservation.builder()
                        .reservationNumber(
                                generateReservationNumber()
                        )
                        .wallet(updatedWallet)
                        .payment(payment)
                        .paymentTransaction(
                                paymentTransaction
                        )
                        .patient(
                                context
                                        .getChargeLine()
                                        .getPatient()
                        )
                        .encounter(
                                context
                                        .getChargeLine()
                                        .getEncounter()
                        )
                        .charge(
                                context
                                        .getChargeLine()
                                        .getCharge()
                        )
                        .chargeLine(
                                context.getChargeLine()
                        )
                        .chargeResponsibility(
                                responsibility
                        )
                        .patientServiceProduct(
                                context
                                        .getPatientServiceProduct()
                        )
                        .originalReservedAmount(
                                reservationAmount
                        )
                        .remainingReservedAmount(
                                reservationAmount
                        )
                        .consumedAmount(zero())
                        .releasedAmount(zero())
                        .currency(
                                context
                                        .getChargeLine()
                                        .getCurrency()
                        )
                        .status(
                                BillingReservationStatus.ACTIVE
                        )
                        .reservedDate(
                                Instant.now()
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .transactionGroupId(
                                context.getTransactionGroupId()
                                        == null
                                        ? UUID.randomUUID()
                                        : context
                                        .getTransactionGroupId()
                        )
                        .build();

        BillingReservation saved =
                billingReservationRepository
                        .saveAndFlush(
                                reservation
                        );

        updateReservedAmountsAfterCreation(
                context,
                saved
        );

        context.setReservation(saved);

        return saved;
    }
    /**
     * Releases all active reservations for one charge line.
     *
     * Used when:
     * - service deleted
     * - quantity = 0
     * - service cancelled
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BigDecimal releaseChargeLineReservations(
            BillingProcessingContext context,
            ReservationReleaseReason releaseReason,
            String notes,
            String releasedBy
    ) {
        validateReleaseContext(
                context,
                releaseReason,
                releasedBy
        );

        Long chargeLineId =
                context.getChargeLine().getId();

        List<BillingReservation> reservations =
                billingReservationRepository
                        .findAllByChargeLine_IdAndStatusOrderByIdAsc(
                                chargeLineId,
                                BillingReservationStatus.ACTIVE
                        );

        BigDecimal totalReleased = zero();

        for (BillingReservation reservation : reservations) {
            BigDecimal released =
                    releaseReservation(
                            reservation,
                            reservation.getRemainingReservedAmount(),
                            releaseReason,
                            notes,
                            releasedBy
                    );

            totalReleased =
                    totalReleased.add(released);
        }

        updateReservedAmountsAfterRelease(
                context
        );

        LOG.info(
                "[RELEASE_LINE] Charge-line reservations released "
                        + "chargeLineId={} count={} totalReleased={}",
                chargeLineId,
                reservations.size(),
                totalReleased
        );

        return totalReleased;
    }

    /**
     * Releases all active reservations for an encounter.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BigDecimal releaseEncounterReservations(
            Long encounterId,
            ReservationReleaseReason releaseReason,
            String notes,
            String releasedBy
    ) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounterId.required"
            );
        }

        validateReleaseData(
                releaseReason,
                releasedBy
        );

        List<BillingReservation> reservations =
                billingReservationRepository
                        .findAllByEncounter_IdAndStatusOrderByIdAsc(
                                encounterId,
                                BillingReservationStatus.ACTIVE
                        );

        BigDecimal totalReleased = zero();

        for (BillingReservation reservation : reservations) {
            BigDecimal released =
                    releaseReservation(
                            reservation,
                            reservation.getRemainingReservedAmount(),
                            releaseReason,
                            notes,
                            releasedBy
                    );

            totalReleased =
                    totalReleased.add(released);

            updateChargeLineReservedAmount(
                    reservation.getChargeLine()
            );

            updatePatientServicePaymentStatus(
                    reservation.getPatientServiceProduct(),
                    reservation.getChargeLine()
            );
        }

        LOG.info(
                "[RELEASE_ENCOUNTER] Encounter reservations released "
                        + "encounterId={} count={} totalReleased={}",
                encounterId,
                reservations.size(),
                totalReleased
        );

        return totalReleased;
    }

    /**
     * Releases part or all of one reservation.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BigDecimal releaseReservation(
            BillingReservation reservation,
            BigDecimal requestedAmount,
            ReservationReleaseReason releaseReason,
            String notes,
            String releasedBy
    ) {
        validateReleaseData(
                releaseReason,
                releasedBy
        );

        BillingReservation lockedReservation =
                lockReservation(reservation);

        BigDecimal remaining =
                money(
                        lockedReservation
                                .getRemainingReservedAmount()
                );

        BigDecimal amount =
                money(requestedAmount);

        if (amount.signum() <= 0) {
            return zero();
        }

        if (amount.compareTo(remaining) > 0) {
            throw new BadRequestAlertException(
                    "Release amount exceeds remaining reserved amount.",
                    ENTITY_NAME,
                    "release.exceedsRemaining"
            );
        }

        billingWalletService.release(
                lockedReservation.getWallet(),
                amount
        );

        lockedReservation.setRemainingReservedAmount(
                remaining.subtract(amount)
        );

        lockedReservation.setReleasedAmount(
                money(
                        lockedReservation.getReleasedAmount()
                ).add(amount)
        );

        lockedReservation.setReleasedDate(
                Instant.now()
        );

        lockedReservation.setReleaseReason(
                releaseReason
        );

        lockedReservation.setReleaseNotes(
                trimToNull(notes)
        );

        lockedReservation.setReleasedBy(
                releasedBy.trim()
        );

        if (lockedReservation
                .getRemainingReservedAmount()
                .signum() == 0) {

            lockedReservation.setStatus(
                    BillingReservationStatus.RELEASED
            );
        }

        billingReservationRepository.save(
                lockedReservation
        );

        updateChargeLineReservedAmount(
                lockedReservation.getChargeLine()
        );

        updatePatientServicePaymentStatus(
                lockedReservation
                        .getPatientServiceProduct(),
                lockedReservation.getChargeLine()
        );

        LOG.info(
                "[RELEASE] Reservation released "
                        + "reservationId={} amount={} remaining={}",
                lockedReservation.getId(),
                amount,
                lockedReservation
                        .getRemainingReservedAmount()
        );

        return amount;
    }

    /**
     * Consumes reserved balance when the service is financially applied.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BigDecimal consumeReservation(
            BillingReservation reservation,
            BigDecimal requestedAmount
    ) {
        BillingReservation lockedReservation =
                lockReservation(reservation);

        BigDecimal remaining =
                money(
                        lockedReservation
                                .getRemainingReservedAmount()
                );

        BigDecimal amount =
                money(requestedAmount);

        if (amount.signum() <= 0) {
            return zero();
        }

        if (amount.compareTo(remaining) > 0) {
            throw new BadRequestAlertException(
                    "Consumption amount exceeds remaining reservation.",
                    ENTITY_NAME,
                    "consume.exceedsRemaining"
            );
        }

        billingWalletService.consumeReserved(
                lockedReservation.getWallet(),
                amount
        );

        lockedReservation.setRemainingReservedAmount(
                remaining.subtract(amount)
        );

        lockedReservation.setConsumedAmount(
                money(
                        lockedReservation.getConsumedAmount()
                ).add(amount)
        );

        lockedReservation.setConsumedDate(
                Instant.now()
        );

        if (lockedReservation
                .getRemainingReservedAmount()
                .signum() == 0) {

            lockedReservation.setStatus(
                    BillingReservationStatus.CONSUMED
            );
        }

        billingReservationRepository.save(
                lockedReservation
        );

        updateChargeLineReservedAmount(
                lockedReservation.getChargeLine()
        );

        updatePatientServicePaymentStatus(
                lockedReservation
                        .getPatientServiceProduct(),
                lockedReservation.getChargeLine()
        );

        LOG.info(
                "[CONSUME] Reservation consumed "
                        + "reservationId={} amount={} remaining={}",
                lockedReservation.getId(),
                amount,
                lockedReservation
                        .getRemainingReservedAmount()
        );

        return amount;
    }

    /**
     * Adjusts reservations after repricing.
     *
     * If the new patient responsibility is lower, release the excess.
     * If higher, the caller must supply another payment source for
     * additional reservation.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BigDecimal releaseExcessAfterRepricing(
            BillingProcessingContext context,
            ReservationReleaseReason releaseReason,
            String releasedBy
    ) {
        validateReleaseContext(
                context,
                releaseReason,
                releasedBy
        );

        BigDecimal required =
                money(
                        context
                                .getPatientResponsibilityAmount()
                );

        BigDecimal currentlyReserved =
                calculateActiveReservedAmount(
                        context.getChargeLine().getId()
                );

        if (currentlyReserved.compareTo(required) <= 0) {
            context.setReservedAmount(
                    currentlyReserved
            );

            return zero();
        }

        BigDecimal excess =
                currentlyReserved.subtract(required);

        List<BillingReservation> reservations =
                billingReservationRepository
                        .findAllByChargeLine_IdAndStatusOrderByIdAsc(
                                context.getChargeLine().getId(),
                                BillingReservationStatus.ACTIVE
                        );

        BigDecimal remainingToRelease = excess;

        for (int index = reservations.size() - 1;
             index >= 0
                     && remainingToRelease.signum() > 0;
             index--) {

            BillingReservation reservation =
                    reservations.get(index);

            BigDecimal releasable =
                    minimum(
                            remainingToRelease,
                            money(
                                    reservation
                                            .getRemainingReservedAmount()
                            )
                    );

            releaseReservation(
                    reservation,
                    releasable,
                    releaseReason,
                    "Reservation adjusted after repricing",
                    releasedBy
            );

            remainingToRelease =
                    remainingToRelease.subtract(
                            releasable
                    );
        }

        context.setReservedAmount(
                calculateActiveReservedAmount(
                        context.getChargeLine().getId()
                )
        );

        return excess.subtract(
                remainingToRelease
        );
    }

    private BigDecimal calculatePaymentReservableAmount(
            BillingPayment payment
    ) {
        BigDecimal usedFromPayment =
                billingReservationRepository
                        .findAllByPayment_IdOrderByIdAsc(
                                payment.getId()
                        )
                        .stream()
                        .map(reservation ->
                                money(
                                        reservation
                                                .getRemainingReservedAmount()
                                )
                                        .add(
                                                money(
                                                        reservation
                                                                .getConsumedAmount()
                                                )
                                        )
                        )
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        BigDecimal available =
                money(payment.getAmount())
                        .subtract(usedFromPayment);

        return available.max(zero());
    }

    private void updateReservedAmountsAfterCreation(
            BillingProcessingContext context,
            BillingReservation reservation
    ) {
        BillingChargeLine chargeLine =
                context.getChargeLine();

        BigDecimal activeReserved =
                calculateActiveReservedAmount(
                        chargeLine.getId()
                );

        chargeLine.setReservedAmount(
                activeReserved
        );

        billingChargeLineRepository.save(
                chargeLine
        );

        PatientServiceAndProduct item =
                context.getPatientServiceProduct();

        item.setPaidAmount(
                activeReserved
        );

        BigDecimal patientAmount =
                money(
                        context.getPatientResponsibilityAmount()
                );

        item.setRemainingAmount(
                patientAmount
                        .subtract(activeReserved)
                        .max(zero())
        );

        item.setPaymentStatus(
                resolvePaymentStatus(
                        patientAmount,
                        activeReserved
                )
        );

        patientServiceAndProductRepository.save(
                item
        );
    }

    private void updateReservedAmountsAfterRelease(
            BillingProcessingContext context
    ) {
        updateChargeLineReservedAmount(
                context.getChargeLine()
        );

        updatePatientServicePaymentStatus(
                context.getPatientServiceProduct(),
                context.getChargeLine()
        );

        context.setReservedAmount(
                money(
                        context
                                .getChargeLine()
                                .getReservedAmount()
                )
        );
    }

    private void updateChargeLineReservedAmount(
            BillingChargeLine chargeLine
    ) {
        BigDecimal activeReserved =
                calculateActiveReservedAmount(
                        chargeLine.getId()
                );

        chargeLine.setReservedAmount(
                activeReserved
        );

        billingChargeLineRepository.save(
                chargeLine
        );
    }

    private void updatePatientServicePaymentStatus(
            PatientServiceAndProduct item,
            BillingChargeLine chargeLine
    ) {
        BigDecimal patientAmount =
                money(
                        chargeLine
                                .getPatientResponsibilityAmount()
                );

        BigDecimal reserved =
                money(
                        chargeLine.getReservedAmount()
                );

        item.setPaidAmount(reserved);

        item.setRemainingAmount(
                patientAmount
                        .subtract(reserved)
                        .max(zero())
        );

        item.setPaymentStatus(
                resolvePaymentStatus(
                        patientAmount,
                        reserved
                )
        );

        patientServiceAndProductRepository.save(
                item
        );
    }

    private PaymentStatus resolvePaymentStatus(
            BigDecimal patientAmount,
            BigDecimal reservedAmount
    ) {
        if (patientAmount.signum() == 0) {
            return PaymentStatus.PAID;
        }

        if (reservedAmount.signum() == 0) {
            return PaymentStatus.PENDING;
        }

        if (reservedAmount.compareTo(
                patientAmount
        ) >= 0) {
            return PaymentStatus.RESERVED;
        }

        return PaymentStatus.PARTIALLY_RESERVED;
    }

    private BigDecimal calculateActiveReservedAmount(
            Long chargeLineId
    ) {
        return billingReservationRepository
                .findAllByChargeLine_IdAndStatusOrderByIdAsc(
                        chargeLineId,
                        BillingReservationStatus.ACTIVE
                )
                .stream()
                .map(
                        BillingReservation::
                                getRemainingReservedAmount
                )
                .map(this::money)
                .reduce(
                        zero(),
                        BigDecimal::add
                );
    }

    private BillingReservation lockReservation(
            BillingReservation reservation
    ) {
        if (reservation == null
                || reservation.getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted reservation is required.",
                    ENTITY_NAME,
                    "reservation.required"
            );
        }

        BillingReservation locked =
                billingReservationRepository
                        .findById(
                                reservation.getId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing reservation not found with id "
                                                + reservation.getId(),
                                        ENTITY_NAME,
                                        "reservation.notfound"
                                )
                        );

        if (locked.getStatus()
                != BillingReservationStatus.ACTIVE) {
            throw new BadRequestAlertException(
                    "Only active reservations may be changed.",
                    ENTITY_NAME,
                    "reservation.notActive"
            );
        }

        return locked;
    }

    private void validateReservationContext(
            BillingProcessingContext context,
            BillingPayment payment
    ) {
        if (context == null
                || context.getPatientServiceProduct() == null
                || context.getChargeLine() == null
                || context.getPatientResponsibility() == null) {

            throw new BadRequestAlertException(
                    "Complete billing context with patient responsibility is required.",
                    ENTITY_NAME,
                    "context.invalid"
            );
        }

        if (payment == null
                || payment.getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted billing payment is required.",
                    ENTITY_NAME,
                    "payment.required"
            );
        }
    }

    private void validatePaymentSource(
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction,
            BillingWallet wallet,
            BillingProcessingContext context
    ) {
        if (payment.getWallet() == null
                || !payment.getWallet()
                .getId()
                .equals(wallet.getId())) {

            throw new BadRequestAlertException(
                    "Payment does not belong to the selected wallet.",
                    ENTITY_NAME,
                    "payment.wallet.mismatch"
            );
        }

        if (payment.getPatient() == null
                || !payment.getPatient()
                .getId()
                .equals(
                        context
                                .getPatientServiceProduct()
                                .getPatientId()
                )) {

            throw new BadRequestAlertException(
                    "Payment does not belong to the patient.",
                    ENTITY_NAME,
                    "payment.patient.mismatch"
            );
        }

        if (payment.getCurrency()
                != context
                .getPatientServiceProduct()
                .getCurrency()) {

            throw new BadRequestAlertException(
                    "Payment currency does not match billing currency.",
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
    }

    private void validateReleaseContext(
            BillingProcessingContext context,
            ReservationReleaseReason releaseReason,
            String releasedBy
    ) {
        if (context == null
                || context.getChargeLine() == null
                || context.getChargeLine().getId() == null) {

            throw new BadRequestAlertException(
                    "Persisted charge line is required.",
                    ENTITY_NAME,
                    "chargeLine.required"
            );
        }

        validateReleaseData(
                releaseReason,
                releasedBy
        );
    }

    private void validateReleaseData(
            ReservationReleaseReason releaseReason,
            String releasedBy
    ) {
        if (releaseReason == null) {
            throw new BadRequestAlertException(
                    "Reservation release reason is required.",
                    ENTITY_NAME,
                    "releaseReason.required"
            );
        }

        if (releasedBy == null
                || releasedBy.isBlank()) {
            throw new BadRequestAlertException(
                    "Released-by user is required.",
                    ENTITY_NAME,
                    "releasedBy.required"
            );
        }
    }

    private String generateReservationNumber() {
        return "RSV-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
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

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BigDecimal releaseActiveReservationsForChargeLine(
            Long chargeLineId,
            ReservationReleaseReason releaseReason,
            String notes,
            String releasedBy
    ) {
        if (chargeLineId == null) {
            throw new BadRequestAlertException(
                    "Charge-line ID is required.",
                    ENTITY_NAME,
                    "chargeLineId.required"
            );
        }

        validateReleaseData(
                releaseReason,
                releasedBy
        );

        List<BillingReservation> reservations =
                billingReservationRepository
                        .findAllByChargeLine_IdAndStatusOrderByReservedDateDescIdDesc(
                                chargeLineId,
                                BillingReservationStatus.ACTIVE
                        );

        BigDecimal totalReleased = zero();

        for (BillingReservation reservation : reservations) {
            BigDecimal remaining =
                    money(
                            reservation.getRemainingReservedAmount()
                    );

            if (remaining.signum() <= 0) {
                continue;
            }

            BigDecimal released =
                    releaseReservation(
                            reservation,
                            remaining,
                            releaseReason,
                            notes,
                            releasedBy
                    );

            totalReleased =
                    totalReleased.add(released);
        }

        return totalReleased;
    }
}