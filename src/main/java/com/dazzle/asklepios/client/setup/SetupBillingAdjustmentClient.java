package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.BillingAdjustmentResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingAdjustmentResolveResponse;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface SetupBillingAdjustmentClient {

    @PostMapping("/api/setup/internal/billing-adjustments/resolve")
    BillingAdjustmentResolveResponse resolveAdjustments(
            @RequestBody
            BillingAdjustmentResolveRequest request
    );
}
