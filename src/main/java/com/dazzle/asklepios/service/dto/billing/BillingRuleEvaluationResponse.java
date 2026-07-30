package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingSettlementPath;
import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;

import java.io.Serializable;

public record BillingRuleEvaluationResponse(

        boolean ruleFound,

        Long billingRuleId,

        String billingRuleName,

        BillingItemTypes billingItemType,

        BillingTrigger billingTrigger,

        BillingSettlementPath settlementPath,

        BillingEventType billingEvent,

        boolean eventMatches,

        boolean billsOnCurrentEvent,

        String message

) implements Serializable {

    public static BillingRuleEvaluationResponse missingRule(
            BillingItemTypes billingItemType,
            BillingEventType billingEvent
    ) {
        return new BillingRuleEvaluationResponse(
                false,
                null,
                null,
                billingItemType,
                null,
                null,
                billingEvent,
                false,
                false,
                "No billing rule is configured for "
                        + billingItemType
                        + ". Add a default rule in Billing Rule Setup "
                        + "or assign a rule to this catalog item."
        );
    }
}
