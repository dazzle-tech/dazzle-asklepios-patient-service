package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "setup-service",
        contextId = "setupBillingPricingClient",
        path = "/api/setup"
)
public interface SetupBillingPricingClient {

    @PostMapping("/internal/billing-pricing/resolve")
    BillingPricingResolveResponse resolve(
            @RequestBody
            BillingPricingResolveRequest request
    );
}