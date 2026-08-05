package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCancellationReason;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ReservationReleaseReason;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationResult;
import com.dazzle.asklepios.service.dto.billing.BillingChargeLineReversalResult;
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
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class BillingCancellationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingCancellationService.class
            );

    private static final String ENTITY_NAME =
            "billingCancellation";

    private static final int MONEY_SCALE = 4;

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final BillingChargeLineRepository
            billingChargeLineRepository;

    private final BillingChargeRepository
            billingChargeRepository;

    private final BillingAllocationService
            billingAllocationService;

    private final BillingReservationService
            billingReservationService;

    private final BillingResponsibilityService
            billingResponsibilityService;

    private final BillingPricingSnapshotService
            billingPricingSnapshotService;

    private final BillingWalletService
            billingWalletService;

    private final BillingAllocationReversalCoordinator
            billingAllocationReversalCoordinator;

    @Transactional(rollbackFor = Exception.class)
    public BillingCancellationResult cancelPatientService(
            BillingCancellationRequest request
    ) {
        validateRequest(request);

        PatientServiceAndProduct item =
                patientServiceAndProductRepository
                        .findById(
                                request.patientServiceProductId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient service/product not found with id "
                                                + request
                                                .patientServiceProductId(),
                                        ENTITY_NAME,
                                        "patientServiceProduct.notfound"
                                )
                        );

        BillingChargeLine chargeLine =
                billingChargeLineRepository
                        .findFirstByPatientServiceProduct_IdOrderByIdDesc(
                                item.getId()
                        )
                        .orElse(null);

        /*
         * The service may have been added but never financially charged.
         */
        if (chargeLine == null) {

            LOG.info(
                    "[CANCEL_UNCHARGED] Uncharged patient item cancelled "
                            + "pspId={} reason={} requestId={}",
                    item.getId(),
                    request.cancellationReason(),
                    request.requestId()
            );

            cancelUnchargedPatientItem(
                    item,
                    request
            );

            return new BillingCancellationResult(
                    item.getId(),
                    null,
                    null,
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    true
            );
        }

        if (isChargeLineAlreadyCancelled(
                chargeLine
        )) {
            BillingWallet wallet =
                    loadWalletIfExists(item);

            return buildResult(
                    item,
                    chargeLine,
                    new BillingChargeLineReversalResult(
                            zero(),
                            zero(),
                            zero()
                    ),
                    zero(),
                    wallet
            );
        }

        BillingCharge charge =
                billingChargeRepository
                        .findById(
                                chargeLine
                                        .getCharge()
                                        .getId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing charge not found.",
                                        ENTITY_NAME,
                                        "charge.notfound"
                                )
                        );

        LOG.info(
                "[CANCEL_START] Cancelling billing service "
                        + "pspId={} chargeLineId={} chargeId={} "
                        + "reason={} requestId={}",
                item.getId(),
                chargeLine.getId(),
                charge.getId(),
                request.cancellationReason(),
                request.requestId()
        );

        /*
         * 1. Reverse consumed and allocated money.
         */
        BillingChargeLineReversalResult reversalResult =
                billingAllocationReversalCoordinator
                        .reverseActiveAllocationsForChargeLine(
                                chargeLine.getId(),
                                request.reason(),
                                request.cancelledBy(),
                                request.requestId(),
                                request.sourceChannel()
                        );

        /*
         * 2. Release money that is still only reserved.
         */
        BigDecimal releasedReservationAmount =
                billingReservationService
                        .releaseActiveReservationsForChargeLine(
                                chargeLine.getId(),
                                resolveReleaseReason(
                                        request.cancellationReason()
                                ),
                                request.reason(),
                                request.cancelledBy()
                        );

        /*
         * 3. Cancel patient/insurance responsibilities.
         */
        billingResponsibilityService
                .cancelResponsibilitiesForChargeLine(
                        chargeLine.getId(),
                        request.reason()
                );


        /*
         * 4. Cancel active pricing snapshot.
         */
        cancelPricingSnapshot(
                chargeLine,
                item,
                request
        );

        /*
         * 5. Cancel charge line.
         */
        cancelChargeLine(
                chargeLine,
                request
        );

        /*
         * 6. Cancel PSP operational state.
         */
        cancelPatientItem(
                item,
                request
        );

        /*
         * 7. Recalculate header from all non-cancelled lines.
         */
        recalculateChargeHeader(
                charge,
                request
        );

        BillingWallet wallet =
                loadWalletIfExists(item);

        LOG.info(
                "[CANCEL_COMPLETE] Billing service cancelled "
                        + "pspId={} chargeLineId={} "
                        + "releasedReservation={}",
                item.getId(),
                chargeLine.getId(),
                releasedReservationAmount
        );

        return buildResult(
                item,
                chargeLine,
                reversalResult,
                releasedReservationAmount,
                wallet
        );
    }

    private void cancelPricingSnapshot(
            BillingChargeLine chargeLine,
            PatientServiceAndProduct item,
            BillingCancellationRequest request
    ) {
        /*
         * Build only the context required by cancelActiveSnapshot().
         */
        com.dazzle.asklepios.service.dto.billing
                .BillingProcessingContext context =
                com.dazzle.asklepios.service.dto.billing
                        .BillingProcessingContext.builder()
                        .chargeLine(chargeLine)
                        .patientServiceProduct(item)
                        .idempotencyKey(
                                "CANCEL:"
                                        + request.requestId()
                        )
                        .build();

        billingPricingSnapshotService
                .cancelActiveSnapshot(
                        context,
                        request.reason()
                );
    }

    private void cancelChargeLine(
            BillingChargeLine chargeLine,
            BillingCancellationRequest request
    ) {
        chargeLine.setStatus(
                BillingChargeLineStatus.CANCELLED
        );

        chargeLine.setCancelledDate(
                Instant.now()
        );

        chargeLine.setCancelledBy(
                request.cancelledBy()
                        .trim()
        );

        chargeLine.setCancellationReason(
                request.reason()
                        .trim()
        );

        /*
         * Zero accounting amounts while keeping DB checks valid:
         *   gross = quantity * unit_price
         *   net = gross - discount - exemption + tax
         *   net = patient + insurance + other
         *   net = allocated + outstanding
         *   reserved <= patient - allocated
         * Quantity stays > 0 (entity/DB minimum); unit price goes to zero.
         */
        zeroChargeLineAmounts(chargeLine);

        chargeLine.setNotes(
                appendNote(
                        chargeLine.getNotes(),
                        "Cancelled/removed from billing-accounting. Amounts zeroed. "
                                + request.reason().trim()
                )
        );

        billingChargeLineRepository.save(
                chargeLine
        );
    }

    private void zeroChargeLineAmounts(BillingChargeLine chargeLine) {
        BigDecimal z = zero();

        chargeLine.setUnitPrice(z);
        chargeLine.setGrossAmount(z);
        chargeLine.setDiscountAmount(z);
        chargeLine.setExemptionAmount(z);
        chargeLine.setTaxAmount(z);
        chargeLine.setNetAmount(z);

        chargeLine.setPatientResponsibilityAmount(z);
        chargeLine.setInsuranceResponsibilityAmount(z);
        chargeLine.setOtherPayerResponsibilityAmount(z);

        chargeLine.setAllocatedAmount(z);
        chargeLine.setOutstandingAmount(z);
        chargeLine.setReservedAmount(z);
    }

    private void cancelPatientItem(
            PatientServiceAndProduct item,
            BillingCancellationRequest request
    ) {
        zeroPatientItemAmounts(item);

        item.setPaymentStatus(
                PaymentStatus.CANCELLED
        );

        /*
         * The existing table does not have a dedicated cancelled flag.
         * Keep isBilled based on your operational rule.
         */
        item.setIsBilled(false);

        item.setBillingInvoiceId(null);
        item.setBillingInvoiceItemId(null);
        item.setPaymentId(null);

        item.setNotes(
                appendNote(
                        item.getNotes(),
                        "Billing cancelled/removed. Amounts zeroed. Remaining=0. "
                                + request.reason()
                )
        );

        patientServiceAndProductRepository.save(
                item
        );
    }

    private void cancelUnchargedPatientItem(
            PatientServiceAndProduct item,
            BillingCancellationRequest request
    ) {
        zeroPatientItemAmounts(item);
        item.setPaymentStatus(
                PaymentStatus.CANCELLED
        );
        item.setIsBilled(false);
        item.setBillingInvoiceId(null);
        item.setBillingInvoiceItemId(null);
        item.setPaymentId(null);

        item.setNotes(
                appendNote(
                        item.getNotes(),
                        "Uncharged service cancelled/removed. Amounts zeroed. Remaining=0. "
                                + request.reason()
                )
        );

        patientServiceAndProductRepository.save(
                item
        );
    }

    private void zeroPatientItemAmounts(PatientServiceAndProduct item) {
        BigDecimal z = zero();

        item.setUnitPrice(z);
        item.setDiscountAmount(z);
        item.setExemptionAmount(z);
        item.setTaxAmount(z);
        item.setTotalAmount(z);
        item.setGrossAmount(z);
        item.setNetAmount(z);
        item.setPatientShareAmount(z);
        item.setInsuranceShareAmount(z);
        item.setPaidAmount(z);
        item.setRemainingAmount(z);
    }

    private void recalculateChargeHeader(
            BillingCharge charge,
            BillingCancellationRequest request
    ) {
        /*
         * Recalculate from financially active lines only.
         */
        java.util.List<BillingChargeLine> activeLines =
                billingChargeLineRepository
                        .findAllByCharge_IdAndStatusNotInOrderByIdAsc(
                                charge.getId(),
                                EnumSet.of(
                                        BillingChargeLineStatus.CANCELLED,
                                        BillingChargeLineStatus.REVERSED
                                )
                        );

        BigDecimal gross = zero();
        BigDecimal discount = zero();
        BigDecimal exemption = zero();
        BigDecimal tax = zero();
        BigDecimal net = zero();
        BigDecimal allocated = zero();
        BigDecimal outstanding = zero();

        for (BillingChargeLine line : activeLines) {
            gross = gross.add(
                    money(line.getGrossAmount())
            );

            discount = discount.add(
                    money(line.getDiscountAmount())
            );

            exemption = exemption.add(
                    money(line.getExemptionAmount())
            );

            tax = tax.add(
                    money(line.getTaxAmount())
            );

            net = net.add(
                    money(line.getNetAmount())
            );

            allocated = allocated.add(
                    money(line.getAllocatedAmount())
            );

            outstanding = outstanding.add(
                    money(line.getOutstandingAmount())
            );
        }

        charge.setGrossAmount(gross);
        charge.setDiscountAmount(discount);
        charge.setExemptionAmount(exemption);
        charge.setTaxAmount(tax);
        charge.setNetAmount(net);
        charge.setAllocatedAmount(allocated);
        charge.setOutstandingAmount(outstanding);
        charge.setLineCount(activeLines.size());

        if (activeLines.isEmpty()) {
            charge.setStatus(
                    BillingChargeStatus.CANCELLED
            );

            charge.setCancelledDate(
                    Instant.now()
            );

            charge.setCancelledBy(
                    request.cancelledBy().trim()
            );

            charge.setCancellationReason(
                    "All billing charge lines were cancelled. "
                            + request.reason().trim()
            );
        }

        billingChargeRepository.save(charge);
    }

    private BillingWallet loadWalletIfExists(
            PatientServiceAndProduct item
    ) {
        return billingWalletService
                .findOptionalByPatientAndCurrency(
                        item.getPatientId(),
                        item.getCurrency()
                );
    }

    private BillingCancellationResult buildResult(
            PatientServiceAndProduct item,
            BillingChargeLine chargeLine,
            BillingChargeLineReversalResult reversalResult,
            BigDecimal releasedReservationAmount,
            BillingWallet wallet
    ) {
        BillingChargeLineReversalResult normalizedReversal =
                reversalResult == null
                        ? new BillingChargeLineReversalResult(
                        zero(),
                        zero(),
                        zero()
                )
                        : reversalResult;

        return new BillingCancellationResult(
                item.getId(),

                chargeLine.getCharge() == null
                        ? null
                        : chargeLine
                        .getCharge()
                        .getId(),

                chargeLine.getId(),

                money(
                        normalizedReversal
                                .walletAllocationReversedAmount()
                ),

                money(
                        normalizedReversal
                                .debitAllocationReversedAmount()
                ),

                money(
                        normalizedReversal
                                .totalReversedAmount()
                ),

                money(releasedReservationAmount),

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
                ),

                true
        );
    }

    private ReservationReleaseReason resolveReleaseReason(
            BillingCancellationReason reason
    ) {
        return switch (reason) {
            case SERVICE_DELETED ->
                    ReservationReleaseReason.SERVICE_CANCELLED;

            case QUANTITY_ZERO ->
                    ReservationReleaseReason.QUANTITY_ZERO;

            case ENCOUNTER_CANCELLED ->
                    ReservationReleaseReason.ENCOUNTER_CANCELLED;

            case SERVICE_CANCELLED,
                 ORDER_CANCELLED,
                 CLINICAL_DECISION ->
                    ReservationReleaseReason.SERVICE_CANCELLED;

            case DUPLICATE_ENTRY,
                 MANUAL_CANCELLATION ->
                    ReservationReleaseReason.MANUAL_RELEASE;
        };
    }

    private boolean isChargeLineAlreadyCancelled(
            BillingChargeLine chargeLine
    ) {
        return chargeLine.getStatus()
                == BillingChargeLineStatus.CANCELLED;
    }

    private void validateRequest(
            BillingCancellationRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Billing cancellation request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.patientServiceProductId() == null) {
            throw new BadRequestAlertException(
                    "Patient service/product ID is required.",
                    ENTITY_NAME,
                    "patientServiceProductId.required"
            );
        }

        if (request.cancellationReason() == null) {
            throw new BadRequestAlertException(
                    "Cancellation reason type is required.",
                    ENTITY_NAME,
                    "cancellationReason.required"
            );
        }

        if (request.reason() == null
                || request.reason().isBlank()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required.",
                    ENTITY_NAME,
                    "reason.required"
            );
        }

        if (request.cancelledBy() == null
                || request.cancelledBy().isBlank()) {
            throw new BadRequestAlertException(
                    "Cancelled-by user is required.",
                    ENTITY_NAME,
                    "cancelledBy.required"
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

    private String appendNote(
            String currentNotes,
            String newNote
    ) {
        if (currentNotes == null
                || currentNotes.isBlank()) {
            return newNote;
        }

        return currentNotes
                + System.lineSeparator()
                + newNote;
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
