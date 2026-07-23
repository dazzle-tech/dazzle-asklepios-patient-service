package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.BillingRuleResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "setup-service",
        contextId = "billingRuleClient"
)
public interface BillingRuleClient {

    @GetMapping("/api/setup/billing-rule/{id}")
    BillingRuleResponse getById(
            @PathVariable("id") Long id
    );

    @GetMapping(
            "/api/setup/billing-rule/default/{billingItemType}"
    )
    BillingRuleResponse getDefault(
            @PathVariable("billingItemType")
            String billingItemType
    );
}