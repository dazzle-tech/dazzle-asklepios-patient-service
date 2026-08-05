package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingSettlementPath;
import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;
import com.dazzle.asklepios.service.dto.billing.BillingRuleEvaluationRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRuleEvaluationResponse;
import com.dazzle.asklepios.service.dto.billing.BillingRuleResolveResponse;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingRuleEvaluationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingRuleEvaluationService.class
            );

    private static final String ENTITY_NAME =
            "billingRuleEvaluation";

    private final CatalogBillingRuleLookupService
            catalogBillingRuleLookupService;

    private final SetupBillingRuleService
            setupBillingRuleService;

    private final BillingTriggerMatcher
            billingTriggerMatcher;

    @Transactional(readOnly = true)
    public BillingRuleEvaluationResponse evaluate(
            BillingRuleEvaluationRequest request
    ) {
        validateRequest(request);

        Long catalogBillingRuleId =
                catalogBillingRuleLookupService.lookupBillingRuleId(
                        request
                );

        BillingRuleResolveResponse resolvedRule;

        try {
            resolvedRule =
                    setupBillingRuleService.resolveForCatalogItem(
                            request.billingItemType(),
                            catalogBillingRuleId
                    );
        } catch (NotFoundAlertException exception) {
            LOG.info(
                    "[EVALUATE] No billing rule configured itemType={} catalogRuleId={}",
                    request.billingItemType(),
                    catalogBillingRuleId
            );

            return BillingRuleEvaluationResponse.missingRule(
                    request.billingItemType(),
                    request.billingEvent()
            );
        }

        boolean eventMatches =
                billingTriggerMatcher.matches(
                        resolvedRule.billingTrigger(),
                        request.billingEvent()
                );

        BillingSettlementPath settlementPath =
                mapSettlementPath(
                        resolvedRule.billingTrigger()
                );

        String message =
                buildMessage(
                        resolvedRule,
                        settlementPath,
                        request.billingEvent(),
                        eventMatches
                );

        LOG.debug(
                "[EVALUATE] itemType={} ruleId={} trigger={} event={} "
                        + "matches={} settlementPath={}",
                request.billingItemType(),
                resolvedRule.billingRuleId(),
                resolvedRule.billingTrigger(),
                request.billingEvent(),
                eventMatches,
                settlementPath
        );

        return new BillingRuleEvaluationResponse(
                true,
                resolvedRule.billingRuleId(),
                resolvedRule.billingRuleName(),
                resolvedRule.billingItemType(),
                resolvedRule.billingTrigger(),
                settlementPath,
                request.billingEvent(),
                eventMatches,
                eventMatches,
                message
        );
    }

    public void requireConfiguredRule(
            PatientServiceProductCreateDTO dto,
            BillingEventType billingEvent
    ) {
        BillingRuleEvaluationResponse evaluation =
                evaluate(
                        toEvaluationRequest(
                                dto,
                                billingEvent
                        )
                );

        if (!evaluation.ruleFound()) {
            throw new BadRequestAlertException(
                    evaluation.message(),
                    ENTITY_NAME,
                    "billingRule.notConfigured"
            );
        }
    }

    public void requireConfiguredRule(
            BillingItemTypes billingItemType,
            Long diagnosticTestId,
            BillingEventType billingEvent
    ) {
        BillingRuleEvaluationResponse evaluation =
                evaluate(
                        new BillingRuleEvaluationRequest(
                                billingItemType,
                                billingEvent,
                                null,
                                null,
                                diagnosticTestId,
                                null
                        )
                );

        if (!evaluation.ruleFound()) {
            throw new BadRequestAlertException(
                    evaluation.message(),
                    ENTITY_NAME,
                    "billingRule.notConfigured"
            );
        }
    }

    private BillingRuleEvaluationRequest toEvaluationRequest(
            PatientServiceProductCreateDTO dto,
            BillingEventType billingEvent
    ) {
        return new BillingRuleEvaluationRequest(
                dto.billingItemType(),
                billingEvent,
                dto.serviceId(),
                dto.procedureId(),
                dto.diagnosticTestId(),
                dto.brandMedicationId()
        );
    }

    private void validateRequest(
            BillingRuleEvaluationRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Billing rule evaluation request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.billingItemType() == null) {
            throw new BadRequestAlertException(
                    "Billing item type is required.",
                    ENTITY_NAME,
                    "billingItemType.required"
            );
        }

        if (request.billingEvent() == null) {
            throw new BadRequestAlertException(
                    "Billing event is required.",
                    ENTITY_NAME,
                    "billingEvent.required"
            );
        }
    }

    private BillingSettlementPath mapSettlementPath(
            BillingTrigger trigger
    ) {
        if (trigger == null) {
            return BillingSettlementPath.REMAINING_TO_PAY;
        }

        return switch (trigger) {
            case CHECKOUT ->
                    BillingSettlementPath.LEDGER_DEBIT_AT_CHECKOUT;
            case MANUAL ->
                    BillingSettlementPath.MANUAL;
            default ->
                    BillingSettlementPath.REMAINING_TO_PAY;
        };
    }

    private String buildMessage(
            BillingRuleResolveResponse rule,
            BillingSettlementPath settlementPath,
            BillingEventType billingEvent,
            boolean eventMatches
    ) {
        String ruleLabel =
                rule.billingRuleName() == null
                        ? "Billing rule"
                        : rule.billingRuleName();

        if (!eventMatches) {
            return ruleLabel
                    + " is configured to bill on "
                    + formatTrigger(rule.billingTrigger())
                    + ". The current action ("
                    + formatEvent(billingEvent)
                    + ") will not create a charge yet.";
        }

        return switch (settlementPath) {
            case LEDGER_DEBIT_AT_CHECKOUT ->
                    ruleLabel
                            + " will defer billing until checkout. "
                            + "Any unpaid patient share may be posted "
                            + "to ledger debit during checkout.";
            case MANUAL ->
                    ruleLabel
                            + " requires manual billing preparation "
                            + "before collection.";
            case REMAINING_TO_PAY ->
                    ruleLabel
                            + " will add this item to remaining to pay "
                            + "when saved.";
        };
    }

    private String formatTrigger(BillingTrigger trigger) {
        if (trigger == null) {
            return "the configured trigger";
        }

        return switch (trigger) {
            case ENCOUNTER_CREATED -> "encounter creation";
            case TREATMENT_STARTED -> "treatment start";
            case ORDERED -> "order placement";
            case DISPENSED -> "dispense";
            case SERVICE_COMPLETED -> "service completion";
            case CHECKOUT -> "checkout";
            case MANUAL -> "manual billing";
            case DEBIT_NOTE -> "debit note";
        };
    }

    private String formatEvent(BillingEventType eventType) {
        if (eventType == null) {
            return "this action";
        }

        return switch (eventType) {
            case ENCOUNTER_CREATED -> "encounter creation";
            case TREATMENT_STARTED -> "treatment start";
            case ITEM_ORDERED -> "order";
            case ITEM_DISPENSED -> "dispense";
            case SERVICE_COMPLETED -> "service completion";
            case CHECKOUT -> "checkout";
            case MANUAL -> "manual billing";
            case DEBIT_NOTE -> "debit note";
            case ITEM_UPDATED -> "item update";
            case ITEM_CANCELLED -> "item cancellation";
            case ENCOUNTER_CANCELLED -> "encounter cancellation";
        };
    }
}
