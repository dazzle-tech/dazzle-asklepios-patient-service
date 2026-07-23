package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;

public record BillingRuleResponse(
        Long id,
        String name,
        String billingItemType,
        BillingTrigger billingTrigger
) {
}