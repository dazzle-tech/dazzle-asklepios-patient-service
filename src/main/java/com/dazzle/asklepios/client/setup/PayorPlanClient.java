package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface PayorPlanClient {
    @GetMapping("/api/setup/payor-plan/{id}")
    ResponseEntity<Void> existsPayorPlan(@PathVariable("id") @NotNull Long payorPlanId );

}
