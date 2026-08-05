package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BillingRuleResponse(

        Long id,

        String name,

        String billingItemType,

        BillingTrigger billingTrigger,

        Boolean isDefault

) implements Serializable {
}