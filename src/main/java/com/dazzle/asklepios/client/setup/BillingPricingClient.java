package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.SetupPricingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "setup-service", contextId = "billingPricingClient")
public interface BillingPricingClient {

    @GetMapping("/api/setup/price-list-setups/resolve")
    SetupPricingResponse resolvePricing(
            @RequestParam String billingItemType,
            @RequestParam Long itemId,
            @RequestParam Long patientId,
            @RequestParam Long encounterId,
            @RequestParam(required = false) Long patientInsuranceId,
            @RequestParam String currency
    );
}