package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;

import java.io.Serializable;

public record BillingRuleResolveResponse(

        Long billingRuleId,

        String billingRuleName,

        BillingItemTypes billingItemType,

        BillingTrigger billingTrigger

) implements Serializable {
}