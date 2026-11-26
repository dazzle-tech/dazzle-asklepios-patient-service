package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.InsuranceRegistrationStatusDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "setupServiceClient",
        contextId = "insuranceRegistrationStatusClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface InsuranceRegistrationStatusClient {

    @GetMapping("/api/setup/insurance-registration-status")
    InsuranceRegistrationStatusDTO check(
            @RequestParam(value = "payerNphiesId", required = false) String payerNphiesId,
            @RequestParam(value = "policyNumber", required = false) String policyNumber,
            @RequestParam(value = "className", required = false) String className
    );
}
