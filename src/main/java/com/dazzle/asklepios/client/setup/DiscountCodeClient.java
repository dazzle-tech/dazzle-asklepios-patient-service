package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.DiscountCodeSetupDTO;
import com.dazzle.asklepios.client.setup.dto.DiscountCodeValidateRequest;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "setupServiceClient",
        contextId = "discountCodeClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface DiscountCodeClient {

    @PostMapping("/api/setup/discount-code/validate")
    DiscountCodeSetupDTO validate(@RequestBody DiscountCodeValidateRequest request);
}
