package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveRequest;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationResult;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutResult;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingRefundRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundResult;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalResult;
import com.dazzle.asklepios.service.dto.billing.BillingRuleResolveResponse;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BillingEngineService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingEngineService.class
            );

    private static final String ENTITY_NAME =
            "billingEngine";

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final SetupBillingRuleService
            setupBillingRuleService;

    private final BillingTriggerMatcher
            billingTriggerMatcher;

    private final SetupBillingPricingService
            setupBillingPricingService;

    private final BillingPricingInputFactory
            billingPricingInputFactory;

    /*
     * All local financial transactions are orchestrated
     * through BillingTransactionService.
     */
    private final BillingTransactionService
            billingTransactionService;

    /*
     * ============================================================
     * MAIN BILLING PROCESS
     * ============================================================
     */

    /**
     * Main billing entry point for one patient service/product.
     *
     * Setup Service calls happen before the local financial
     * transaction begins.
     *
     * Pricing resolution order:
     *
     * 1. Applicable active Price List.
     * 2. Setup item base-price fallback.
     * 3. Error if no valid price exists.
     */
    public BillingOperationResult process(
            Long patientServiceProductId,
            BillingEventType eventType,
            Long facilityId,
            String requestId
    ) {
        validateInput(
                patientServiceProductId,
                eventType,
                facilityId,
                requestId
        );

        LOG.info(
                "[PROCESS] Billing started "
                        + "pspId={} eventType={} "
                        + "facilityId={} requestId={}",
                patientServiceProductId,
                eventType,
                facilityId,
                requestId
        );

        PatientServiceAndProduct item =
                findPatientServiceProduct(
                        patientServiceProductId
                );

        validatePatientItem(item);

        /*
         * An exempted item is still processed.
         *
         * It must produce:
         *
         * - pricing snapshot
         * - charge line
         * - exemption audit record
         *
         * It is not skipped.
         */
        BillingRuleResolveResponse billingRule =
                setupBillingRuleService.resolve(
                        item
                );

        validateBillingRule(
                item,
                billingRule
        );

        boolean triggerMatches =
                billingTriggerMatcher.matches(
                        billingRule.billingTrigger(),
                        eventType
                );

        if (!triggerMatches) {
            LOG.info(
                    "[PROCESS] Billing skipped because trigger "
                            + "does not match "
                            + "pspId={} configuredTrigger={} "
                            + "eventType={}",
                    item.getId(),
                    billingRule.billingTrigger(),
                    eventType
            );

            return BillingOperationResult.skipped(
                    item.getId(),
                    "Configured billing trigger "
                            + billingRule.billingTrigger()
                            + " does not match event "
                            + eventType
            );
        }

        Long sourceId =
                resolveSourceId(
                        item
                );

        Long payerId =
                resolvePayerId(
                        item
                );

        BillingPricingResolveRequest pricingRequest =
                buildPricingRequest(
                        item,
                        facilityId,
                        sourceId,
                        payerId
                );

        /*
         * Resolution order:
         *
         * Price List
         *     ↓ when no applicable item exists
         * Setup item base price
         *     ↓ when no valid base price exists
         * Error
         */
        ResolvedBillingPrice resolvedPrice =
                setupBillingPricingService
                        .resolveOrFallback(
                                pricingRequest
                        );

        validateResolvedPricing(
                item,
                resolvedPrice
        );

        BillingPricingInput pricingInput =
                billingPricingInputFactory.create(
                        item,
                        resolvedPrice
                );

        /*
         * Local database transaction begins inside
         * BillingTransactionService.
         */
        BillingOperationResult result =
                billingTransactionService
                        .createPatientItemBilling(
                                item.getId(),
                                billingRule,
                                pricingInput,
                                eventType,
                                requestId.trim()
                        );

        LOG.info(
                "[PROCESS] Billing completed "
                        + "pspId={} chargeId={} "
                        + "chargeLineId={} netAmount={} "
                        + "priceSource={} processed={}",
                result.patientServiceProductId(),
                result.chargeId(),
                result.chargeLineId(),
                result.netAmount(),
                resolvedPrice.priceSource(),
                result.processed()
        );

        return result;
    }

    /*
     * ============================================================
     * BILLING EVENT SHORTCUTS
     * ============================================================
     */

    /**
     * Called when a default service is added while creating
     * an encounter.
     */
    public BillingOperationResult onEncounterCreated(
            Long patientServiceProductId,
            Long facilityId,
            String requestId
    ) {
        return process(
                patientServiceProductId,
                BillingEventType.ENCOUNTER_CREATED,
                facilityId,
                requestId
        );
    }

    /**
     * Called when treatment starts.
     */
    public BillingOperationResult onTreatmentStarted(
            Long patientServiceProductId,
            Long facilityId,
            String requestId
    ) {
        return process(
                patientServiceProductId,
                BillingEventType.TREATMENT_STARTED,
                facilityId,
                requestId
        );
    }

    /**
     * Called when a clinical item is ordered.
     */
    public BillingOperationResult onItemOrdered(
            Long patientServiceProductId,
            Long facilityId,
            String requestId
    ) {
        return process(
                patientServiceProductId,
                BillingEventType.ITEM_ORDERED,
                facilityId,
                requestId
        );
    }

    /**
     * Called when medication is dispensed.
     */
    public BillingOperationResult onItemDispensed(
            Long patientServiceProductId,
            Long facilityId,
            String requestId
    ) {
        return process(
                patientServiceProductId,
                BillingEventType.ITEM_DISPENSED,
                facilityId,
                requestId
        );
    }

    /**
     * Called when service execution is completed.
     */
    public BillingOperationResult onServiceCompleted(
            Long patientServiceProductId,
            Long facilityId,
            String requestId
    ) {
        return process(
                patientServiceProductId,
                BillingEventType.SERVICE_COMPLETED,
                facilityId,
                requestId
        );
    }

    /**
     * Manual billing operation.
     */
    public BillingOperationResult onManualBilling(
            Long patientServiceProductId,
            Long facilityId,
            String requestId
    ) {
        return process(
                patientServiceProductId,
                BillingEventType.MANUAL,
                facilityId,
                requestId
        );
    }

    /*
     * ============================================================
     * REPRICING
     * ============================================================
     */

    /**
     * Repricing after changing:
     *
     * - quantity
     * - insurance
     * - patient share
     * - service information
     * - pricing-effective date
     */
    public BillingOperationResult reprice(
            Long patientServiceProductId,
            Long facilityId,
            String requestId
    ) {
        validateInput(
                patientServiceProductId,
                BillingEventType.ITEM_UPDATED,
                facilityId,
                requestId
        );

        PatientServiceAndProduct item =
                findPatientServiceProduct(
                        patientServiceProductId
                );

        validatePatientItem(
                item
        );

        BillingRuleResolveResponse billingRule =
                setupBillingRuleService.resolve(
                        item
                );

        validateBillingRule(
                item,
                billingRule
        );

        Long sourceId =
                resolveSourceId(
                        item
                );

        Long payerId =
                resolvePayerId(
                        item
                );

        BillingPricingResolveRequest pricingRequest =
                buildPricingRequest(
                        item,
                        facilityId,
                        sourceId,
                        payerId
                );

        ResolvedBillingPrice resolvedPrice =
                setupBillingPricingService
                        .resolveOrFallback(
                                pricingRequest
                        );

        validateResolvedPricing(
                item,
                resolvedPrice
        );

        BillingPricingInput pricingInput =
                billingPricingInputFactory.create(
                        item,
                        resolvedPrice
                );

        BillingOperationResult result =
                billingTransactionService
                        .repricePatientItemBilling(
                                item.getId(),
                                billingRule,
                                pricingInput,
                                requestId.trim()
                        );

        LOG.info(
                "[REPRICE] Billing repricing completed "
                        + "pspId={} chargeId={} "
                        + "chargeLineId={} netAmount={} "
                        + "priceSource={}",
                result.patientServiceProductId(),
                result.chargeId(),
                result.chargeLineId(),
                result.netAmount(),
                resolvedPrice.priceSource()
        );

        return result;
    }

    /*
     * ============================================================
     * CHECKOUT
     * ============================================================
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
                "[CHECKOUT] Billing checkout requested "
                        + "chargeId={} requestId={} checkoutBy={}",
                request.chargeId(),
                request.requestId(),
                request.checkoutBy()
        );

        BillingCheckoutResult result =
                billingTransactionService.checkout(
                        request
                );

        LOG.info(
                "[CHECKOUT] Billing checkout completed "
                        + "chargeId={} status={} "
                        + "patientOutstanding={} "
                        + "totalOutstanding={} "
                        + "financiallyClosed={}",
                result.chargeId(),
                result.chargeStatus(),
                result.patientOutstandingAmount(),
                result.totalOutstandingAmount(),
                result.financiallyClosed()
        );

        return result;
    }

    /*
     * ============================================================
     * SERVICE CANCELLATION
     * ============================================================
     */

    /**
     * Cancels one patient service/product financially.
     *
     * The transaction service coordinates:
     *
     * - wallet allocation reversal
     * - debit allocation reversal
     * - reservation release
     * - responsibility cancellation
     * - snapshot cancellation
     * - charge-line cancellation
     * - charge-header recalculation
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
                "[CANCEL_SERVICE] Billing cancellation requested "
                        + "pspId={} cancellationReason={} "
                        + "requestId={}",
                request.patientServiceProductId(),
                request.cancellationReason(),
                request.requestId()
        );

        BillingCancellationResult result =
                billingTransactionService
                        .cancelPatientService(
                                request
                        );

        LOG.info(
                "[CANCEL_SERVICE] Billing cancellation completed "
                        + "pspId={} chargeId={} "
                        + "chargeLineId={} "
                        + "walletReversed={} "
                        + "debitReversed={} "
                        + "reservationReleased={} "
                        + "cancelled={}",
                result.patientServiceProductId(),
                result.chargeId(),
                result.chargeLineId(),
                result.walletAllocationReversedAmount(),
                result.debitAllocationReversedAmount(),
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
                "[REFUND] Billing refund requested "
                        + "patientId={} originalPaymentId={} "
                        + "amount={} source={} requestId={}",
                request.patientId(),
                request.originalPaymentId(),
                request.requestedAmount(),
                request.refundSourceType(),
                request.requestId()
        );

        BillingRefundResult result =
                billingTransactionService.refund(
                        request
                );

        LOG.info(
                "[REFUND] Billing refund completed "
                        + "refundId={} refundNumber={} "
                        + "amount={} status={} "
                        + "walletAvailable={}",
                result.refundId(),
                result.refundNumber(),
                result.refundedAmount(),
                result.status(),
                result.walletAvailableBalance()
        );

        return result;
    }

    /*
     * ============================================================
     * REFUND REVERSAL
     * ============================================================
     */

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
                "[REVERSE_REFUND] Refund reversal requested "
                        + "refundId={} amount={} requestId={}",
                request.refundId(),
                request.amount(),
                request.requestId()
        );

        BillingRefundReversalResult result =
                billingTransactionService.reverseRefund(
                        request
                );

        LOG.info(
                "[REVERSE_REFUND] Refund reversal completed "
                        + "originalRefundId={} reversalRefundId={} "
                        + "amount={} originalStatus={} "
                        + "walletAvailable={}",
                result.originalRefundId(),
                result.reversalRefundId(),
                result.reversedAmount(),
                result.originalRefundStatus(),
                result.walletAvailableBalance()
        );

        return result;
    }

    /*
     * ============================================================
     * ENCOUNTER CANCELLATION
     * ============================================================
     */

    /**
     * Temporary encounter-level entry point.
     *
     * BillingTransactionService must internally cancel each patient
     * service/product through BillingCancellationService.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelEncounter(
            Long encounterId,
            String reason,
            String requestId
    ) {
        validateEncounterCancellationInput(
                encounterId,
                reason,
                requestId
        );

        LOG.info(
                "[CANCEL_ENCOUNTER] Encounter billing cancellation "
                        + "requested encounterId={} requestId={}",
                encounterId,
                requestId
        );

        billingTransactionService
                .cancelEncounterBilling(
                        encounterId,
                        reason.trim(),
                        requestId.trim()
                );

        LOG.info(
                "[CANCEL_ENCOUNTER] Encounter billing cancellation "
                        + "completed encounterId={} requestId={}",
                encounterId,
                requestId
        );
    }

    /*
     * ============================================================
     * PRICING REQUEST
     * ============================================================
     */

    private BillingPricingResolveRequest buildPricingRequest(
            PatientServiceAndProduct item,
            Long facilityId,
            Long sourceId,
            Long payerId
    ) {
        return new BillingPricingResolveRequest(
                facilityId,
                item.getPatientId(),
                item.getEncounterId(),
                item.getBillingItemType(),
                sourceId,
                item.getPatientInsuranceId(),
                payerId,
                item.getCurrency(),
                resolveTaxApplicableOn(
                        item
                ),
                resolveDiscountApplicableOn(
                        item
                ),
                LocalDate.now()
        );
    }

    /*
     * ============================================================
     * ENTITY LOADERS
     * ============================================================
     */

    private PatientServiceAndProduct findPatientServiceProduct(
            Long id
    ) {
        return patientServiceAndProductRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient service/product not found with id "
                                        + id,
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

    private void validatePatientItem(
            PatientServiceAndProduct item
    ) {
        if (item.getPatientId() == null) {
            throw new BadRequestAlertException(
                    "Patient ID is missing from patient service/product.",
                    ENTITY_NAME,
                    "patientId.required"
            );
        }

        if (item.getEncounterId() == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is missing from patient service/product.",
                    ENTITY_NAME,
                    "encounterId.required"
            );
        }

        if (item.getBillingItemType() == null) {
            throw new BadRequestAlertException(
                    "Billing item type is missing.",
                    ENTITY_NAME,
                    "billingItemType.required"
            );
        }

        if (item.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Currency is missing.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        if (item.getQuantity() == null
                || item.getQuantity() <= 0) {
            throw new BadRequestAlertException(
                    "Quantity must be greater than zero. "
                            + "Use billing cancellation for quantity zero.",
                    ENTITY_NAME,
                    "quantity.invalid"
            );
        }
    }

    private void validateBillingRule(
            PatientServiceAndProduct item,
            BillingRuleResolveResponse rule
    ) {
        if (rule == null) {
            throw new BadRequestAlertException(
                    "Setup Service returned no billing rule.",
                    ENTITY_NAME,
                    "billingRule.notfound"
            );
        }

        if (rule.billingRuleId() == null) {
            throw new BadRequestAlertException(
                    "Resolved billing-rule ID is missing.",
                    ENTITY_NAME,
                    "billingRuleId.missing"
            );
        }

        if (rule.billingTrigger() == null) {
            throw new BadRequestAlertException(
                    "Resolved billing trigger is missing.",
                    ENTITY_NAME,
                    "billingTrigger.missing"
            );
        }

        if (rule.billingItemType() != null
                && rule.billingItemType()
                != item.getBillingItemType()) {

            throw new BadRequestAlertException(
                    "Billing rule item type does not match patient item type.",
                    ENTITY_NAME,
                    "billingRule.itemType.mismatch"
            );
        }
    }

    private void validateResolvedPricing(
            PatientServiceAndProduct item,
            ResolvedBillingPrice resolvedPrice
    ) {
        if (resolvedPrice == null) {
            throw new BadRequestAlertException(
                    "No billing price could be resolved.",
                    ENTITY_NAME,
                    "pricing.notfound"
            );
        }

        if (resolvedPrice.priceSource() == null) {
            throw new BadRequestAlertException(
                    "Resolved billing price source is missing.",
                    ENTITY_NAME,
                    "priceSource.missing"
            );
        }

        if (resolvedPrice.unitPrice() == null
                || resolvedPrice.unitPrice().signum() < 0) {
            throw new BadRequestAlertException(
                    "Resolved unit price is invalid.",
                    ENTITY_NAME,
                    "unitPrice.invalid"
            );
        }

        if (resolvedPrice.currency() == null) {
            throw new BadRequestAlertException(
                    "Resolved pricing currency is missing.",
                    ENTITY_NAME,
                    "pricingCurrency.missing"
            );
        }

        if (resolvedPrice.currency()
                != item.getCurrency()) {
            throw new BadRequestAlertException(
                    "Resolved pricing currency does not match item currency.",
                    ENTITY_NAME,
                    "pricingCurrency.mismatch"
            );
        }

        if (resolvedPrice.resolvedFromPriceList()) {
            if (resolvedPrice.priceListId() == null) {
                throw new BadRequestAlertException(
                        "Resolved price-list ID is missing.",
                        ENTITY_NAME,
                        "priceListId.missing"
                );
            }

            if (resolvedPrice.priceListItemId() == null) {
                throw new BadRequestAlertException(
                        "Resolved price-list item ID is missing.",
                        ENTITY_NAME,
                        "priceListItemId.missing"
                );
            }

            if (resolvedPrice.pricingResponse() == null) {
                throw new BadRequestAlertException(
                        "Price-list response metadata is missing.",
                        ENTITY_NAME,
                        "pricingResponse.missing"
                );
            }
        }

        if (resolvedPrice.resolvedFromSetupFallback()
                && resolvedPrice.setupSourceId() == null) {

            throw new BadRequestAlertException(
                    "Setup source ID is required for fallback pricing.",
                    ENTITY_NAME,
                    "setupSourceId.missing"
            );
        }
    }

    private void validateInput(
            Long patientServiceProductId,
            BillingEventType eventType,
            Long facilityId,
            String requestId
    ) {
        if (patientServiceProductId == null) {
            throw new BadRequestAlertException(
                    "Patient service/product ID is required.",
                    ENTITY_NAME,
                    "patientServiceProductId.required"
            );
        }

        if (eventType == null) {
            throw new BadRequestAlertException(
                    "Billing event type is required.",
                    ENTITY_NAME,
                    "eventType.required"
            );
        }

        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Facility ID is required.",
                    ENTITY_NAME,
                    "facilityId.required"
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

    private void validateEncounterCancellationInput(
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

        if (reason == null
                || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Encounter cancellation reason is required.",
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
     * SOURCE AND PAYER RESOLUTION
     * ============================================================
     */

    private Long resolvePayerId(
            PatientServiceAndProduct item
    ) {
        /*
         * Temporary behavior:
         *
         * SELF_PAY:
         * payerId = null
         *
         * INSURANCE:
         * This must be replaced in the Default Service insurance
         * phase by loading PatientInsurance and returning its payerId.
         */
        return null;
    }

    private Long resolveSourceId(
            PatientServiceAndProduct item
    ) {
        if (item.getSourceId() != null) {
            return item.getSourceId();
        }

        return switch (item.getBillingItemType()) {

            case SERVICE ->
                    requireSourceId(
                            item.getServiceId(),
                            "service"
                    );

            case PROCEDURE ->
                    requireSourceId(
                            item.getProcedureId(),
                            "procedure"
                    );

            case MEDICATION ->
                    requireSourceId(
                            item.getBrandMedicationId(),
                            "brand medication"
                    );

            case LABORATORY,
                 RADIOLOGY,
                 PATHOLOGY ->
                    requireSourceId(
                            item.getDiagnosticTestId(),
                            "diagnostic test"
                    );
        };
    }

    private Long requireSourceId(
            Long sourceId,
            String sourceName
    ) {
        if (sourceId == null) {
            throw new BadRequestAlertException(
                    "Missing " + sourceName + " ID.",
                    ENTITY_NAME,
                    "sourceId.required"
            );
        }

        return sourceId;
    }

    /*
     * ============================================================
     * TAX AND DISCOUNT APPLICABILITY
     * ============================================================
     */

    private TaxApplicableOn resolveTaxApplicableOn(
            PatientServiceAndProduct item
    ) {
        return switch (item.getBillingItemType()) {

            case SERVICE,
                 PROCEDURE,
                 LABORATORY,
                 RADIOLOGY,
                 PATHOLOGY ->
                    TaxApplicableOn.SERVICE;

            case MEDICATION ->
                    TaxApplicableOn.PRODUCT;
        };
    }

    private DiscountApplicableOn resolveDiscountApplicableOn(
            PatientServiceAndProduct item
    ) {
        return switch (item.getBillingItemType()) {

            case SERVICE,
                 PROCEDURE,
                 LABORATORY,
                 RADIOLOGY,
                 PATHOLOGY ->
                    DiscountApplicableOn.SERVICE;

            case MEDICATION ->
                    DiscountApplicableOn.PRODUCT;
        };
    }
}