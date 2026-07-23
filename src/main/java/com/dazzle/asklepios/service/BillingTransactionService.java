package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationResult;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutResult;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.BillingRefundRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundResult;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalResult;
import com.dazzle.asklepios.service.dto.billing.BillingRuleResolveResponse;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingTransactionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingTransactionService.class
            );

    private static final String ENTITY_NAME =
            "billingTransaction";

    /*
     * ============================================================
     * REPOSITORIES
     * ============================================================
     */

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    /*
     * ============================================================
     * BILLING CORE SERVICES
     * ============================================================
     */

    private final BillingPricingService
            billingPricingService;

    private final BillingChargeService
            billingChargeService;

    private final BillingCheckoutService
            billingCheckoutService;

    private final BillingCancellationService
            billingCancellationService;

    private final BillingRefundService
            billingRefundService;
    private final BillingPricingSnapshotService billingPricingSnapshotService;
    private final BillingResponsibilityService billingResponsibilityService;
    private final BillingWalletService billingWalletService;
    private final BillingPaymentRepository billingPaymentRepository;
    private final BillingReservationService billingReservationService;

    /*
     * ============================================================
     * CREATE PATIENT ITEM BILLING
     * ============================================================
     */

    /**
     * Creates the first financial charge for a patient
     * service/product.
     *
     * Setup Service calls must already be completed before entering
     * this local financial transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public BillingOperationResult createPatientItemBilling(
            Long patientServiceProductId,
            BillingRuleResolveResponse billingRule,
            BillingPricingInput pricingInput,
            BillingEventType eventType,
            String requestId
    ) {
        validateCreateInput(
                patientServiceProductId,
                billingRule,
                pricingInput,
                eventType,
                requestId
        );

        LOG.info(
                "[CREATE] Billing transaction started "
                        + "pspId={} eventType={} "
                        + "ruleId={} requestId={}",
                patientServiceProductId,
                eventType,
                billingRule.billingRuleId(),
                requestId
        );

        PatientServiceAndProduct item =
                findPatientServiceProduct(
                        patientServiceProductId
                );

        BillingProcessingContext context =
                buildContext(
                        item,
                        billingRule,
                        pricingInput,
                        eventType,
                        requestId,
                        "CREATE"
                );

        /*
         * 1. Pricing calculation.
         */
        billingPricingService.calculate(
                context
        );

        /*
         * 2. Charge header.
         */
        billingChargeService.createOrLoadCharge(
                context
        );

        /*
         * 3. Charge line.
         */
        billingChargeService.createChargeLine(
                context
        );

        /*
         * 4. Historical pricing snapshot.
         */
        billingPricingSnapshotService.createInitialSnapshot(
                context
        );

        /*
         * 5. Patient / insurance responsibility.
         */
        billingResponsibilityService.calculate(
                context
        );

        /*
         * 6. Header totals after responsibility calculation.
         */
        billingChargeService.recalculateChargeTotals(
                context
        );

        BillingOperationResult result =
                buildResult(
                        context,
                        true,
                        resolveCreateMessage(context)
                );

        LOG.info(
                "[CREATE] Billing transaction completed "
                        + "pspId={} chargeId={} lineId={} "
                        + "snapshotId={} patientAmount={} "
                        + "insuranceAmount={} gross={} "
                        + "exemption={} tax={} net={}",
                result.patientServiceProductId(),
                result.chargeId(),
                result.chargeLineId(),
                result.pricingSnapshotId(),
                result.patientResponsibilityAmount(),
                result.insuranceResponsibilityAmount(),
                result.grossAmount(),
                result.exemptionAmount(),
                result.taxAmount(),
                result.netAmount()
        );

        return result;
    }

    /*
     * ============================================================
     * REPRICING
     * ============================================================
     */

    /**
     * Recalculates an existing charge line after changing:
     *
     * - quantity
     * - item price
     * - tax
     * - discount
     * - exemption
     * - insurance context
     */
    @Transactional(rollbackFor = Exception.class)
    public BillingOperationResult repricePatientItemBilling(
            Long patientServiceProductId,
            BillingRuleResolveResponse billingRule,
            BillingPricingInput pricingInput,
            String requestId
    ) {
        validateRepricingInput(
                patientServiceProductId,
                billingRule,
                pricingInput,
                requestId
        );

        LOG.info(
                "[REPRICE] Billing repricing started "
                        + "pspId={} ruleId={} requestId={}",
                patientServiceProductId,
                billingRule.billingRuleId(),
                requestId
        );

        PatientServiceAndProduct item =
                findPatientServiceProduct(
                        patientServiceProductId
                );

        BillingProcessingContext context =
                buildContext(
                        item,
                        billingRule,
                        pricingInput,
                        BillingEventType.ITEM_UPDATED,
                        requestId,
                        "REPRICE"
                );

        /*
         * 1. Load the current active line.
         */
        billingChargeService.loadExistingChargeLine(
                context
        );

        /*
         * 2. Calculate the new financial values.
         */
        billingPricingService.calculate(
                context
        );

        /*
         * 3. Apply the new pricing.
         *
         * BillingChargeService must reject repricing when:
         *
         * new net amount < already allocated amount
         */
        billingChargeService
                .applyPricingToExistingChargeLine(
                        context
                );

        /*
         * 4. Recalculate the charge header.
         */
        billingChargeService
                .recalculateChargeTotals(
                        context
                );

        BillingOperationResult result =
                buildResult(
                        context,
                        true,
                        "Billing item repriced successfully"
                );

        LOG.info(
                "[REPRICE] Billing repricing completed "
                        + "pspId={} chargeId={} lineId={} net={}",
                result.patientServiceProductId(),
                result.chargeId(),
                result.chargeLineId(),
                result.netAmount()
        );

        return result;
    }

    /*
     * ============================================================
     * CHECKOUT
     * ============================================================
     */

    /**
     * Financial checkout orchestration.
     *
     * BillingCheckoutService handles:
     *
     * - reservation allocation
     * - available-wallet allocation
     * - debit creation when allowed
     * - debit allocation
     * - patient responsibility settlement
     * - charge closing
     */
    @Transactional(rollbackFor = Exception.class)
    public BillingCheckoutResult checkout(
            BillingCheckoutRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Billing checkout request is required.",
                    ENTITY_NAME,
                    "checkoutRequest.required"
            );
        }

        LOG.info(
                "[CHECKOUT] Billing checkout transaction started "
                        + "chargeId={} requestId={} checkoutBy={}",
                request.chargeId(),
                request.requestId(),
                request.checkoutBy()
        );

        BillingCheckoutResult result =
                billingCheckoutService.checkout(
                        request
                );

        LOG.info(
                "[CHECKOUT] Billing checkout transaction completed "
                        + "chargeId={} status={} "
                        + "patientOutstanding={} "
                        + "insuranceOutstanding={} "
                        + "totalOutstanding={} "
                        + "financiallyClosed={}",
                result.chargeId(),
                result.chargeStatus(),
                result.patientOutstandingAmount(),
                result.insuranceOutstandingAmount(),
                result.totalOutstandingAmount(),
                result.financiallyClosed()
        );

        return result;
    }

    /*
     * ============================================================
     * PATIENT SERVICE CANCELLATION
     * ============================================================
     */

    /**
     * Cancels one patient service/product through the complete
     * financial cancellation flow.
     *
     * BillingCancellationService handles:
     *
     * - reversing wallet allocations
     * - reversing debit allocations
     * - releasing reservations
     * - cancelling responsibilities
     * - cancelling pricing snapshots
     * - cancelling the charge line
     * - recalculating the charge header
     */
    @Transactional(rollbackFor = Exception.class)
    public BillingCancellationResult cancelPatientService(
            BillingCancellationRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Billing cancellation request is required.",
                    ENTITY_NAME,
                    "cancellationRequest.required"
            );
        }

        LOG.info(
                "[CANCEL_SERVICE] Billing cancellation transaction "
                        + "started pspId={} reasonType={} "
                        + "requestId={}",
                request.patientServiceProductId(),
                request.cancellationReason(),
                request.requestId()
        );

        BillingCancellationResult result =
                billingCancellationService
                        .cancelPatientService(
                                request
                        );

        LOG.info(
                "[CANCEL_SERVICE] Billing cancellation transaction "
                        + "completed pspId={} chargeId={} "
                        + "chargeLineId={} walletReversed={} "
                        + "debitReversed={} totalReversed={} "
                        + "reservationReleased={} cancelled={}",
                result.patientServiceProductId(),
                result.chargeId(),
                result.chargeLineId(),
                result.walletAllocationReversedAmount(),
                result.debitAllocationReversedAmount(),
                result.totalReversedAllocationAmount(),
                result.releasedReservationAmount(),
                result.cancelled()
        );

        return result;
    }

    /*
     * ============================================================
     * REFUND
     * ============================================================
     */

    /**
     * Refunds money from the patient's available wallet balance.
     *
     * Reserved and consumed balances cannot be refunded directly.
     */
    @Transactional(rollbackFor = Exception.class)
    public BillingRefundResult refund(
            BillingRefundRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Billing refund request is required.",
                    ENTITY_NAME,
                    "refundRequest.required"
            );
        }

        LOG.info(
                "[REFUND] Billing refund transaction started "
                        + "patientId={} originalPaymentId={} "
                        + "amount={} source={} requestId={}",
                request.patientId(),
                request.originalPaymentId(),
                request.requestedAmount(),
                request.refundSourceType(),
                request.requestId()
        );

        BillingRefundResult result =
                billingRefundService
                        .refundAvailableBalance(
                                request
                        );

        LOG.info(
                "[REFUND] Billing refund transaction completed "
                        + "refundId={} refundNumber={} "
                        + "amount={} status={} "
                        + "walletAvailable={} walletRefunded={}",
                result.refundId(),
                result.refundNumber(),
                result.refundedAmount(),
                result.status(),
                result.walletAvailableBalance(),
                result.walletRefundedAmount()
        );

        return result;
    }


    /*
     * ============================================================
     * LEGACY ITEM CANCELLATION
     * ============================================================
     */

    /**
     * Legacy method kept temporarily for old callers.
     *
     * New code must use:
     *
     * cancelPatientService(BillingCancellationRequest)
     *
     * This legacy method only works when the line has no active
     * reservation or allocation.
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void cancelPatientItemBilling(
            Long patientServiceProductId,
            String reason,
            String requestId
    ) {
        validateCancellationInput(
                patientServiceProductId,
                reason,
                requestId
        );

        LOG.warn(
                "[CANCEL_ITEM_LEGACY] Legacy cancellation called "
                        + "pspId={} requestId={}",
                patientServiceProductId,
                requestId
        );

        PatientServiceAndProduct item =
                findPatientServiceProduct(
                        patientServiceProductId
                );

        BillingProcessingContext context =
                BillingProcessingContext.builder()
                        .transactionGroupId(
                                UUID.randomUUID()
                        )
                        .idempotencyKey(
                                buildIdempotencyKey(
                                        "CANCEL_LEGACY",
                                        patientServiceProductId,
                                        requestId
                                )
                        )
                        .eventType(
                                BillingEventType.ITEM_CANCELLED
                        )
                        .patientServiceProduct(item)
                        .build();

        billingChargeService.loadExistingChargeLine(
                context
        );

        /*
         * This service rejects cancellation when an allocation
         * or reservation still exists.
         */
        billingChargeService.cancelChargeLine(
                context,
                reason.trim(),
                currentUser()
        );

        LOG.info(
                "[CANCEL_ITEM_LEGACY] Legacy cancellation completed "
                        + "pspId={} chargeId={} lineId={}",
                patientServiceProductId,
                context.getChargeId(),
                context.getChargeLineId()
        );
    }

    /*
     * ============================================================
     * LEGACY ENCOUNTER CANCELLATION
     * ============================================================
     */

    /**
     * Temporary legacy encounter cancellation.
     *
     * This method only succeeds if all encounter charge lines have:
     *
     * allocatedAmount = 0
     * reservedAmount = 0
     *
     * A complete encounter-cancellation coordinator will later cancel
     * every PSP through BillingCancellationService.
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void cancelEncounterBilling(
            Long encounterId,
            String reason,
            String requestId
    ) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounterId.required"
            );
        }

        validateReasonAndRequestId(
                reason,
                requestId
        );

        LOG.warn(
                "[CANCEL_ENCOUNTER_LEGACY] Legacy encounter "
                        + "cancellation started encounterId={} "
                        + "reason={} requestId={}",
                encounterId,
                reason,
                requestId
        );

        billingChargeService.cancelEncounterCharges(
                encounterId,
                reason.trim(),
                currentUser()
        );

        LOG.info(
                "[CANCEL_ENCOUNTER_LEGACY] Legacy encounter "
                        + "cancellation completed encounterId={}",
                encounterId
        );
    }

    /*
     * ============================================================
     * CONTEXT
     * ============================================================
     */

    private BillingProcessingContext buildContext(
            PatientServiceAndProduct item,
            BillingRuleResolveResponse billingRule,
            BillingPricingInput pricingInput,
            BillingEventType eventType,
            String requestId,
            String operation
    ) {
        return BillingProcessingContext.builder()
                .transactionGroupId(
                        UUID.randomUUID()
                )
                .idempotencyKey(
                        buildIdempotencyKey(
                                operation,
                                item.getId(),
                                requestId
                        )
                )
                .eventType(eventType)
                .billingRuleId(
                        billingRule.billingRuleId()
                )
                .patientServiceProduct(item)
                .pricingInput(pricingInput)
                .patientResponsibilityAmount(
                        BigDecimal.ZERO
                )
                .insuranceResponsibilityAmount(
                        BigDecimal.ZERO
                )
                .otherPayerResponsibilityAmount(
                        BigDecimal.ZERO
                )
                .reservedAmount(
                        BigDecimal.ZERO
                )
                .uncoveredPatientAmount(
                        BigDecimal.ZERO
                )
                .build();
    }

    /*
     * ============================================================
     * RESULT
     * ============================================================
     */

    private BillingOperationResult buildResult(
            BillingProcessingContext context,
            boolean processed,
            String message
    ) {
        PatientServiceAndProduct item =
                context.getPatientServiceProduct();

        PriceCalculationResult pricing =
                context.getPricingResult();

        return new BillingOperationResult(
                item.getId(),

                context.getChargeId(),

                context.getChargeLineId(),

                context.getPricingSnapshotId(),

                pricing == null
                        ? BigDecimal.ZERO
                        : defaultZero(
                        pricing.grossAmount()
                ),

                pricing == null
                        ? BigDecimal.ZERO
                        : defaultZero(
                        pricing.discountAmount()
                ),

                pricing == null
                        ? BigDecimal.ZERO
                        : defaultZero(
                        pricing.exemptionAmount()
                ),

                pricing == null
                        ? BigDecimal.ZERO
                        : defaultZero(
                        pricing.taxAmount()
                ),

                pricing == null
                        ? BigDecimal.ZERO
                        : defaultZero(
                        pricing.netAmount()
                ),

                defaultZero(
                        context
                                .getPatientResponsibilityAmount()
                ),

                defaultZero(
                        context
                                .getInsuranceResponsibilityAmount()
                ),

                defaultZero(
                        context.getReservedAmount()
                ),

                processed,

                message
        );
    }

    private String resolveCreateMessage(
            BillingProcessingContext context
    ) {
        if (context.getPatientServiceProduct() != null
                && Boolean.TRUE.equals(
                context
                        .getPatientServiceProduct()
                        .getIsExempted()
        )) {

            return "Exempted billing charge created successfully";
        }

        return "Billing charge created successfully";
    }

    /*
     * ============================================================
     * ENTITY LOADERS
     * ============================================================
     */

    private PatientServiceAndProduct
    findPatientServiceProduct(
            Long patientServiceProductId
    ) {
        return patientServiceAndProductRepository
                .findById(
                        patientServiceProductId
                )
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
     * VALIDATION
     * ============================================================
     */

    private void validateCreateInput(
            Long patientServiceProductId,
            BillingRuleResolveResponse billingRule,
            BillingPricingInput pricingInput,
            BillingEventType eventType,
            String requestId
    ) {
        validateCommonInput(
                patientServiceProductId,
                billingRule,
                pricingInput,
                requestId
        );

        if (eventType == null) {
            throw new BadRequestAlertException(
                    "Billing event type is required.",
                    ENTITY_NAME,
                    "eventType.required"
            );
        }
    }

    private void validateRepricingInput(
            Long patientServiceProductId,
            BillingRuleResolveResponse billingRule,
            BillingPricingInput pricingInput,
            String requestId
    ) {
        validateCommonInput(
                patientServiceProductId,
                billingRule,
                pricingInput,
                requestId
        );
    }

    private void validateCommonInput(
            Long patientServiceProductId,
            BillingRuleResolveResponse billingRule,
            BillingPricingInput pricingInput,
            String requestId
    ) {
        if (patientServiceProductId == null) {
            throw new BadRequestAlertException(
                    "Patient service/product ID is required.",
                    ENTITY_NAME,
                    "patientServiceProductId.required"
            );
        }

        if (billingRule == null
                || billingRule.billingRuleId() == null) {

            throw new BadRequestAlertException(
                    "Resolved billing rule is required.",
                    ENTITY_NAME,
                    "billingRule.required"
            );
        }

        if (pricingInput == null) {
            throw new BadRequestAlertException(
                    "Resolved billing pricing input is required.",
                    ENTITY_NAME,
                    "pricingInput.required"
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
    }

    private void validateCancellationInput(
            Long patientServiceProductId,
            String reason,
            String requestId
    ) {
        if (patientServiceProductId == null) {
            throw new BadRequestAlertException(
                    "Patient service/product ID is required.",
                    ENTITY_NAME,
                    "patientServiceProductId.required"
            );
        }

        validateReasonAndRequestId(
                reason,
                requestId
        );
    }

    private void validateReasonAndRequestId(
            String reason,
            String requestId
    ) {
        if (reason == null
                || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required.",
                    ENTITY_NAME,
                    "reason.required"
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
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    private String buildIdempotencyKey(
            String operation,
            Long patientServiceProductId,
            String requestId
    ) {
        return "BILLING:"
                + operation
                + ":PSP:"
                + patientServiceProductId
                + ":"
                + requestId.trim();
    }

    private BigDecimal defaultZero(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private String currentUser() {
        /*
         * Replace later with:
         *
         * SecurityUtils.getCurrentUserLogin()
         *         .orElse("system");
         */
        return "system";
    }

    @Transactional(rollbackFor = Exception.class)
    public BillingRefundReversalResult reverseRefund(
            BillingRefundReversalRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Billing refund reversal request is required.",
                    ENTITY_NAME,
                    "refundReversalRequest.required"
            );
        }

        LOG.info(
                "[REVERSE_REFUND] Refund reversal transaction "
                        + "started refundId={} amount={} requestId={}",
                request.refundId(),
                request.amount(),
                request.requestId()
        );

        BillingRefundReversalResult result =
                billingRefundService.reverseRefund(
                        request.refundId(),
                        request.amount(),
                        request.reason(),
                        request.reversedBy(),
                        request.requestId(),
                        request.sourceChannel()
                );

        LOG.info(
                "[REVERSE_REFUND] Refund reversal transaction "
                        + "completed originalRefundId={} "
                        + "reversalRefundId={} amount={} "
                        + "originalStatus={} reversalStatus={}",
                result.originalRefundId(),
                result.reversalRefundId(),
                result.reversedAmount(),
                result.originalRefundStatus(),
                result.reversalStatus()
        );

        return result;
    }

    private BigDecimal reserveAdvanceBalance(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getPatientServiceProduct() == null
                || context.getChargeLine() == null) {

            throw new BadRequestAlertException(
                    "Complete billing context is required before reservation.",
                    ENTITY_NAME,
                    "reservation.context.invalid"
            );
        }

        if (context.getPatientResponsibility() == null) {
            context.setReservedAmount(
                    BigDecimal.ZERO
            );

            return BigDecimal.ZERO;
        }

        BigDecimal patientOutstanding =
                defaultZero(
                        context
                                .getPatientResponsibility()
                                .getOutstandingAmount()
                );

        if (patientOutstanding.signum() <= 0) {
            context.setReservedAmount(
                    BigDecimal.ZERO
            );

            return BigDecimal.ZERO;
        }

        PatientServiceAndProduct item =
                context.getPatientServiceProduct();

        /*
         * Wallet may not exist when the patient has never made
         * an advance payment.
         */
        BillingWallet wallet =
                billingWalletService
                        .findOptionalByPatient(
                                item.getPatientId()
                        );

        if (wallet == null) {
            context.setReservedAmount(
                    BigDecimal.ZERO
            );

            LOG.debug(
                    "[RESERVE_ADVANCE] No wallet found "
                            + "pspId={} patientId={}",
                    item.getId(),
                    item.getPatientId()
            );

            return BigDecimal.ZERO;
        }

        BigDecimal walletAvailable =
                defaultZero(
                        wallet.getAvailableBalance()
                );

        if (walletAvailable.signum() <= 0) {
            context.setReservedAmount(
                    BigDecimal.ZERO
            );

            LOG.debug(
                    "[RESERVE_ADVANCE] Wallet has no available balance "
                            + "pspId={} walletId={}",
                    item.getId(),
                    wallet.getId()
            );

            return BigDecimal.ZERO;
        }

        /*
         * COMPLETED payments are valid sources of advance balance.
         *
         * Ordering by paymentDate ASC gives FIFO reservation.
         */
        List<BillingPayment> completedPayments =
                billingPaymentRepository
                        .findAllByWallet_IdAndStatusInOrderByPaymentDateAscIdAsc(
                                wallet.getId(),
                                EnumSet.of(
                                        BillingPaymentStatus.COMPLETED
                                )
                        );

        if (completedPayments.isEmpty()) {
            context.setReservedAmount(
                    BigDecimal.ZERO
            );

            LOG.warn(
                    "[RESERVE_ADVANCE] Wallet contains available balance "
                            + "but no completed payment source was found "
                            + "pspId={} walletId={} available={}",
                    item.getId(),
                    wallet.getId(),
                    walletAvailable
            );

            return BigDecimal.ZERO;
        }

        BigDecimal reserved =
                billingReservationService
                        .reserveFromPayments(
                                context,
                                completedPayments
                        );

        context.setReservedAmount(
                defaultZero(reserved)
        );

        LOG.info(
                "[RESERVE_ADVANCE] Advance balance reserved "
                        + "pspId={} patientId={} "
                        + "responsibilityOutstanding={} "
                        + "walletAvailableBefore={} reserved={}",
                item.getId(),
                item.getPatientId(),
                patientOutstanding,
                walletAvailable,
                reserved
        );

        return defaultZero(reserved);
    }
}