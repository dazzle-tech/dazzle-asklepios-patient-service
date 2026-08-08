package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolutionRequest;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PriceListSetupClient {

    @GetMapping("/api/setup/price-list-setups/requires-preauth")
    Boolean requiresPreAuthorization(
            @SpringQueryMap
            BillingPricingResolutionRequest request
    );
}
