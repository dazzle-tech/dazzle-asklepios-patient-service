package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)

public interface SetupBillingPricingClient {

    @PostMapping("/api/setup/internal/billing-pricing/resolve")
    BillingPricingResolveResponse resolve(
            @RequestBody
            BillingPricingResolveRequest request
    );
}