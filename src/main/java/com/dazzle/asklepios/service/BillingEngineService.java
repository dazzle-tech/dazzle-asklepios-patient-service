package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingRuleResolveResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BillingEngineService {

//    private static final Logger LOG =
//            LoggerFactory.getLogger(
//                    BillingEngineService.class
//            );
//
//    private static final String ENTITY_NAME =
//            "billingEngine";
//
//    private final PatientServiceAndProductRepository
//            patientServiceAndProductRepository;
//
//    private final SetupBillingRuleService
//            setupBillingRuleService;
//
//    private final BillingTriggerMatcher
//            billingTriggerMatcher;
//
//    private final SetupBillingPricingService
//            setupBillingPricingService;
//
//    private final BillingPricingInputFactory
//            billingPricingInputFactory;
//
//    private final BillingTransactionService
//            billingTransactionService;
//
//    /**
//     * Main billing entry point for a patient service/product.
//     *
//     * This method is intentionally not transactional because it calls
//     * Setup Service before entering the local financial transaction.
//     */
//    public BillingOperationResult process(
//            Long patientServiceProductId,
//            BillingEventType eventType,
//            Long facilityId,
//            String requestId
//    ) {
//        validateInput(
//                patientServiceProductId,
//                eventType,
//                facilityId,
//                requestId
//        );
//
//        LOG.info(
//                "[PROCESS] Billing started pspId={} eventType={} facilityId={} requestId={}",
//                patientServiceProductId,
//                eventType,
//                facilityId,
//                requestId
//        );
//
//        PatientServiceAndProduct item =
//                findPatientServiceProduct(
//                        patientServiceProductId
//                );
//
//        validatePatientItem(item);
//
//        /*
//         * Exemption still creates a charge and pricing snapshot.
//         * It is not skipped, because we need an audit/history record.
//         */
//        BillingRuleResolveResponse billingRule =
//                setupBillingRuleService.resolve(item);
//
//        validateBillingRule(
//                item,
//                billingRule
//        );
//
//        boolean triggerMatches =
//                billingTriggerMatcher.matches(
//                        billingRule.billingTrigger(),
//                        eventType
//                );
//
//        if (!triggerMatches) {
//            LOG.info(
//                    "[PROCESS] Billing skipped because trigger does not match pspId={} configuredTrigger={} eventType={}",
//                    item.getId(),
//                    billingRule.billingTrigger(),
//                    eventType
//            );
//
//            return BillingOperationResult.skipped(
//                    item.getId(),
//                    "Configured billing trigger "
//                            + billingRule.billingTrigger()
//                            + " does not match event "
//                            + eventType
//            );
//        }
//
//        Long sourceId =
//                resolveSourceId(item);
//
//        Long payerId =
//                resolvePayerId(item);
//
//        BillingPricingResolveRequest pricingRequest =
//                new BillingPricingResolveRequest(
//                        facilityId,
//                        item.getPatientId(),
//                        item.getEncounterId(),
//                        item.getBillingItemType(),
//                        sourceId,
//                        item.getPatientInsuranceId(),
//                        payerId,
//                        item.getCurrency(),
//                        resolveTaxApplicableOn(item),
//                        resolveDiscountApplicableOn(item),
//                        LocalDate.now()
//                );
//
//        BillingPricingResolveResponse pricingResponse =
//                setupBillingPricingService.resolve(
//                        pricingRequest
//                );
//
//        validateResolvedPricing(
//                item,
//                pricingResponse
//        );
//
//        BillingPricingInput pricingInput =
//                billingPricingInputFactory.create(
//                        item,
//                        pricingResponse
//                );
//
//        /*
//         * The local database transaction begins here.
//         */
//        BillingOperationResult result =
//                billingTransactionService
//                        .createPatientItemBilling(
//                                item.getId(),
//                                billingRule,
//                                pricingInput,
//                                eventType,
//                                requestId
//                        );
//
//        LOG.info(
//                "[PROCESS] Billing completed pspId={} chargeId={} chargeLineId={} netAmount={} processed={}",
//                result.patientServiceProductId(),
//                result.chargeId(),
//                result.chargeLineId(),
//                result.netAmount(),
//                result.processed()
//        );
//
//        return result;
//    }
//
//    /**
//     * Called when a default service is added while creating the encounter.
//     */
//    public BillingOperationResult onEncounterCreated(
//            Long patientServiceProductId,
//            Long facilityId,
//            String requestId
//    ) {
//        return process(
//                patientServiceProductId,
//                BillingEventType.ENCOUNTER_CREATED,
//                facilityId,
//                requestId
//        );
//    }
//
//    /**
//     * Called when treatment starts.
//     */
//    public BillingOperationResult onTreatmentStarted(
//            Long patientServiceProductId,
//            Long facilityId,
//            String requestId
//    ) {
//        return process(
//                patientServiceProductId,
//                BillingEventType.TREATMENT_STARTED,
//                facilityId,
//                requestId
//        );
//    }
//
//    /**
//     * Called when a clinical service or procedure is ordered.
//     */
//    public BillingOperationResult onItemOrdered(
//            Long patientServiceProductId,
//            Long facilityId,
//            String requestId
//    ) {
//        return process(
//                patientServiceProductId,
//                BillingEventType.ITEM_ORDERED,
//                facilityId,
//                requestId
//        );
//    }
//
//    /**
//     * Called when medication is dispensed.
//     */
//    public BillingOperationResult onItemDispensed(
//            Long patientServiceProductId,
//            Long facilityId,
//            String requestId
//    ) {
//        return process(
//                patientServiceProductId,
//                BillingEventType.ITEM_DISPENSED,
//                facilityId,
//                requestId
//        );
//    }
//
//    /**
//     * Called when service execution is completed.
//     */
//    public BillingOperationResult onServiceCompleted(
//            Long patientServiceProductId,
//            Long facilityId,
//            String requestId
//    ) {
//        return process(
//                patientServiceProductId,
//                BillingEventType.SERVICE_COMPLETED,
//                facilityId,
//                requestId
//        );
//    }
//
//    /**
//     * Manual billing action.
//     */
//    public BillingOperationResult onManualBilling(
//            Long patientServiceProductId,
//            Long facilityId,
//            String requestId
//    ) {
//        return process(
//                patientServiceProductId,
//                BillingEventType.MANUAL,
//                facilityId,
//                requestId
//        );
//    }
//
//    /**
//     * Repricing after changing quantity, insurance, or item information.
//     */
//    public BillingOperationResult reprice(
//            Long patientServiceProductId,
//            Long facilityId,
//            String requestId
//    ) {
//        validateInput(
//                patientServiceProductId,
//                BillingEventType.ITEM_UPDATED,
//                facilityId,
//                requestId
//        );
//
//        PatientServiceAndProduct item =
//                findPatientServiceProduct(
//                        patientServiceProductId
//                );
//
//        validatePatientItem(item);
//
//        BillingRuleResolveResponse billingRule =
//                setupBillingRuleService.resolve(item);
//
//        Long sourceId =
//                resolveSourceId(item);
//
//        BillingPricingResolveResponse pricingResponse =
//                setupBillingPricingService.resolve(
//                        new BillingPricingResolveRequest(
//                                facilityId,
//                                item.getPatientId(),
//                                item.getEncounterId(),
//                                item.getBillingItemType(),
//                                sourceId,
//                                item.getPatientInsuranceId(),
//                                resolvePayerId(item),
//                                item.getCurrency(),
//                                resolveTaxApplicableOn(item),
//                                resolveDiscountApplicableOn(item),
//                                LocalDate.now()
//                        )
//                );
//
//        BillingPricingInput pricingInput =
//                billingPricingInputFactory.create(
//                        item,
//                        pricingResponse
//                );
//
//        return billingTransactionService
//                .repricePatientItemBilling(
//                        item.getId(),
//                        billingRule,
//                        pricingInput,
//                        requestId
//                );
//    }
//
//    /**
//     * Service deletion or quantity changed to zero.
//     */
//    public void cancelPatientItem(
//            Long patientServiceProductId,
//            String reason,
//            String requestId
//    ) {
//        if (patientServiceProductId == null) {
//            throw new BadRequestAlertException(
//                    "Patient service/product ID is required.",
//                    ENTITY_NAME,
//                    "patientServiceProductId.required"
//            );
//        }
//
//        if (reason == null || reason.isBlank()) {
//            throw new BadRequestAlertException(
//                    "Cancellation reason is required.",
//                    ENTITY_NAME,
//                    "reason.required"
//            );
//        }
//
//        if (requestId == null || requestId.isBlank()) {
//            throw new BadRequestAlertException(
//                    "Request ID is required.",
//                    ENTITY_NAME,
//                    "requestId.required"
//            );
//        }
//
//        billingTransactionService.cancelPatientItemBilling(
//                patientServiceProductId,
//                reason.trim(),
//                requestId.trim()
//        );
//    }
//
//    /**
//     * Encounter cancellation releases all reservations first,
//     * then cancels encounter charges.
//     */
//    public void cancelEncounter(
//            Long encounterId,
//            String reason,
//            String requestId
//    ) {
//        if (encounterId == null) {
//            throw new BadRequestAlertException(
//                    "Encounter ID is required.",
//                    ENTITY_NAME,
//                    "encounterId.required"
//            );
//        }
//
//        if (reason == null || reason.isBlank()) {
//            throw new BadRequestAlertException(
//                    "Encounter cancellation reason is required.",
//                    ENTITY_NAME,
//                    "reason.required"
//            );
//        }
//
//        if (requestId == null || requestId.isBlank()) {
//            throw new BadRequestAlertException(
//                    "Request ID is required.",
//                    ENTITY_NAME,
//                    "requestId.required"
//            );
//        }
//
//        billingTransactionService.cancelEncounterBilling(
//                encounterId,
//                reason.trim(),
//                requestId.trim()
//        );
//    }
//
//    private PatientServiceAndProduct findPatientServiceProduct(
//            Long id
//    ) {
//        return patientServiceAndProductRepository
//                .findById(id)
//                .orElseThrow(() ->
//                        new NotFoundAlertException(
//                                "Patient service/product not found with id "
//                                        + id,
//                                ENTITY_NAME,
//                                "patientServiceProduct.notfound"
//                        )
//                );
//    }
//
//    private void validatePatientItem(
//            PatientServiceAndProduct item
//    ) {
//        if (item.getPatientId() == null) {
//            throw new BadRequestAlertException(
//                    "Patient ID is missing from patient service/product.",
//                    ENTITY_NAME,
//                    "patientId.required"
//            );
//        }
//
//        if (item.getEncounterId() == null) {
//            throw new BadRequestAlertException(
//                    "Encounter ID is missing from patient service/product.",
//                    ENTITY_NAME,
//                    "encounterId.required"
//            );
//        }
//
//        if (item.getBillingItemType() == null) {
//            throw new BadRequestAlertException(
//                    "Billing item type is missing.",
//                    ENTITY_NAME,
//                    "billingItemType.required"
//            );
//        }
//
//        if (item.getCurrency() == null) {
//            throw new BadRequestAlertException(
//                    "Currency is missing.",
//                    ENTITY_NAME,
//                    "currency.required"
//            );
//        }
//
//        /*
//         * Quantity zero is a cancellation event, not a pricing event.
//         */
//        if (item.getQuantity() == null
//                || item.getQuantity() <= 0) {
//            throw new BadRequestAlertException(
//                    "Quantity must be greater than zero. "
//                            + "Use cancelPatientItem for quantity zero.",
//                    ENTITY_NAME,
//                    "quantity.invalid"
//            );
//        }
//    }
//
//    private void validateBillingRule(
//            PatientServiceAndProduct item,
//            BillingRuleResolveResponse rule
//    ) {
//        if (rule == null) {
//            throw new BadRequestAlertException(
//                    "Setup Service returned no billing rule.",
//                    ENTITY_NAME,
//                    "billingRule.notfound"
//            );
//        }
//
//        if (rule.billingRuleId() == null) {
//            throw new BadRequestAlertException(
//                    "Resolved billing-rule ID is missing.",
//                    ENTITY_NAME,
//                    "billingRuleId.missing"
//            );
//        }
//
//        if (rule.billingTrigger() == null) {
//            throw new BadRequestAlertException(
//                    "Resolved billing trigger is missing.",
//                    ENTITY_NAME,
//                    "billingTrigger.missing"
//            );
//        }
//
//        if (rule.billingItemType() != null
//                && rule.billingItemType()
//                != item.getBillingItemType()) {
//            throw new BadRequestAlertException(
//                    "Billing rule item type does not match patient item type.",
//                    ENTITY_NAME,
//                    "billingRule.itemType.mismatch"
//            );
//        }
//    }
//
//    private void validateResolvedPricing(
//            PatientServiceAndProduct item,
//            BillingPricingResolveResponse pricing
//    ) {
//        if (pricing == null) {
//            throw new BadRequestAlertException(
//                    "Setup Service returned no pricing data.",
//                    ENTITY_NAME,
//                    "pricing.notfound"
//            );
//        }
//
//        if (pricing.unitPrice() == null
//                || pricing.unitPrice().signum() < 0) {
//            throw new BadRequestAlertException(
//                    "Resolved unit price is invalid.",
//                    ENTITY_NAME,
//                    "unitPrice.invalid"
//            );
//        }
//
//        if (pricing.currency() == null) {
//            throw new BadRequestAlertException(
//                    "Resolved pricing currency is missing.",
//                    ENTITY_NAME,
//                    "pricingCurrency.missing"
//            );
//        }
//
//        if (pricing.currency() != item.getCurrency()) {
//            throw new BadRequestAlertException(
//                    "Resolved pricing currency does not match item currency.",
//                    ENTITY_NAME,
//                    "pricingCurrency.mismatch"
//            );
//        }
//    }
//
//    private Long resolvePayerId(
//            PatientServiceAndProduct item
//    ) {
//        /*
//         * If payer ID is stored directly on PSP, return it here.
//         *
//         * Currently PSP only contains patientInsuranceId, so the payer ID
//         * should normally be resolved when insurance is attached.
//         *
//         * Replace this with your real insurance/payer resolution.
//         */
//        return null;
//    }
//
//    private Long resolveSourceId(
//            PatientServiceAndProduct item
//    ) {
//        if (item.getSourceId() != null) {
//            return item.getSourceId();
//        }
//
//        return switch (item.getBillingItemType()) {
//
//            case SERVICE ->
//                    requireSourceId(
//                            item.getServiceId(),
//                            "service"
//                    );
//
//            case PROCEDURE ->
//                    requireSourceId(
//                            item.getProcedureId(),
//                            "procedure"
//                    );
//
//            case MEDICATION ->
//                    requireSourceId(
//                            item.getBrandMedicationId(),
//                            "brand medication"
//                    );
//
//            case LABORATORY,
//                 RADIOLOGY,
//                 PATHOLOGY ->
//                    requireSourceId(
//                            item.getDiagnosticTestId(),
//                            "diagnostic test"
//                    );
//        };
//    }
//
//    private Long requireSourceId(
//            Long sourceId,
//            String sourceName
//    ) {
//        if (sourceId == null) {
//            throw new BadRequestAlertException(
//                    "Missing " + sourceName + " ID.",
//                    ENTITY_NAME,
//                    "sourceId.required"
//            );
//        }
//
//        return sourceId;
//    }
//
//    private TaxApplicableOn resolveTaxApplicableOn(
//            PatientServiceAndProduct item
//    ) {
//        /*
//         * Use the actual values available in TaxApplicableOn.
//         *
//         * This implementation assumes SERVICE and PRODUCT values exist.
//         */
//        return switch (item.getBillingItemType()) {
//
//            case SERVICE,
//                 PROCEDURE,
//                 LABORATORY,
//                 RADIOLOGY,
//                 PATHOLOGY ->
//                    TaxApplicableOn.SERVICE;
//
//            case MEDICATION ->
//                    TaxApplicableOn.PRODUCT;
//        };
//    }
//
//    private DiscountApplicableOn resolveDiscountApplicableOn(
//            PatientServiceAndProduct item
//    ) {
//        /*
//         * Use the actual values available in DiscountApplicableOn.
//         */
//        return switch (item.getBillingItemType()) {
//
//            case SERVICE,
//                 PROCEDURE,
//                 LABORATORY,
//                 RADIOLOGY,
//                 PATHOLOGY ->
//                    DiscountApplicableOn.SERVICE;
//
//            case MEDICATION ->
//                    DiscountApplicableOn.PRODUCT;
//        };
//    }
//
//    private void validateInput(
//            Long patientServiceProductId,
//            BillingEventType eventType,
//            Long facilityId,
//            String requestId
//    ) {
//        if (patientServiceProductId == null) {
//            throw new BadRequestAlertException(
//                    "Patient service/product ID is required.",
//                    ENTITY_NAME,
//                    "patientServiceProductId.required"
//            );
//        }
//
//        if (eventType == null) {
//            throw new BadRequestAlertException(
//                    "Billing event type is required.",
//                    ENTITY_NAME,
//                    "eventType.required"
//            );
//        }
//
//        if (facilityId == null) {
//            throw new BadRequestAlertException(
//                    "Facility ID is required.",
//                    ENTITY_NAME,
//                    "facilityId.required"
//            );
//        }
//
//        if (requestId == null || requestId.isBlank()) {
//            throw new BadRequestAlertException(
//                    "Request ID is required.",
//                    ENTITY_NAME,
//                    "requestId.required"
//            );
//        }
//    }
}