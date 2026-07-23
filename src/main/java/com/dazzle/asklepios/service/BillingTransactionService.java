package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
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

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final BillingPricingService
            billingPricingService;

    private final BillingChargeService
            billingChargeService;

    /**
     * Creates the first financial charge for a patient service/product.
     *
     * The Setup Service calls must already be completed before entering
     * this method.
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
                        + "pspId={} eventType={} ruleId={} requestId={}",
                patientServiceProductId,
                eventType,
                billingRule.billingRuleId(),
                requestId
        );

        /*
         * Reload inside the local transaction.
         *
         * Do not use the detached item that was loaded by
         * BillingEngineService before the Setup Service calls.
         */
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
         * 1. Calculate gross, discount, exemption, tax and net.
         */
        billingPricingService.calculate(context);

        /*
         * 2. Create or load the encounter charge header.
         */
        billingChargeService.createOrLoadCharge(context);

        /*
         * 3. Create the line linked to PatientServiceAndProduct.
         */
        billingChargeService.createChargeLine(context);

        /*
         * 4. Recalculate charge-header totals from all active lines.
         */
        billingChargeService.recalculateChargeTotals(context);

        BillingOperationResult result =
                buildResult(
                        context,
                        true,
                        resolveCreateMessage(context)
                );

        LOG.info(
                "[CREATE] Billing transaction completed "
                        + "pspId={} chargeId={} lineId={} "
                        + "gross={} exemption={} tax={} net={}",
                result.patientServiceProductId(),
                result.chargeId(),
                result.chargeLineId(),
                result.grossAmount(),
                result.exemptionAmount(),
                result.taxAmount(),
                result.netAmount()
        );

        return result;
    }

    /**
     * Recalculates an already-created charge line.
     *
     * Used when quantity, item pricing, discount, tax, insurance context
     * or exemption changes.
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
         * Load the active line before applying the new price.
         */
        billingChargeService.loadExistingChargeLine(
                context
        );

        /*
         * Calculate the new values.
         */
        billingPricingService.calculate(context);

        /*
         * Update the existing charge line.
         *
         * BillingChargeService must reject repricing when the new net
         * is below an already allocated amount.
         */
        billingChargeService
                .applyPricingToExistingChargeLine(
                        context
                );

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

    /**
     * Cancels one patient service/product financial line.
     *
     * For now, BillingChargeService prevents cancellation if active
     * reservation or allocation amounts exist.
     *
     * After BillingReservationService is implemented, reservation release
     * will happen here before cancelling the charge line.
     */
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

        LOG.info(
                "[CANCEL_ITEM] Billing cancellation started "
                        + "pspId={} reason={} requestId={}",
                patientServiceProductId,
                reason,
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
                                        "CANCEL",
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
         * Later:
         *
         * billingReservationService.releaseReservation(context);
         * billingAllocationService.reverseAllocations(context);
         */

        billingChargeService.cancelChargeLine(
                context,
                reason.trim(),
                currentUser()
        );

        LOG.info(
                "[CANCEL_ITEM] Billing cancellation completed "
                        + "pspId={} chargeId={} lineId={}",
                patientServiceProductId,
                context.getChargeId(),
                context.getChargeLineId()
        );
    }

    /**
     * Cancels all financial charges for an encounter.
     *
     * Active reservations and allocations must be released first.
     * The current BillingChargeService will reject cancellation while
     * those values remain active.
     */
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

        LOG.info(
                "[CANCEL_ENCOUNTER] Encounter billing cancellation "
                        + "started encounterId={} reason={} requestId={}",
                encounterId,
                reason,
                requestId
        );

        /*
         * Later:
         *
         * billingReservationService.releaseEncounterReservations(...);
         * billingAllocationService.reverseEncounterAllocations(...);
         */

        billingChargeService.cancelEncounterCharges(
                encounterId,
                reason.trim(),
                currentUser()
        );

        LOG.info(
                "[CANCEL_ENCOUNTER] Encounter billing cancellation "
                        + "completed encounterId={}",
                encounterId
        );
    }

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

    private PatientServiceAndProduct
    findPatientServiceProduct(
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
         * استبدليه لاحقاً بـ:
         *
         * SecurityUtils.getCurrentUserLogin()
         *        .orElse("system");
         */
        return "system";
    }
}