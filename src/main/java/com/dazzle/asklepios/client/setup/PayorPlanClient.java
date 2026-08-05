package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PayorPlanDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface PayorPlanClient {
    @GetMapping("/api/setup/payor-plan/{id}")
    ResponseEntity<Void> existsPayorPlan(@PathVariable("id") @NotNull Long payorPlanId );

    @GetMapping("/api/setup/payor-plan/cchi/by-payor/{payorId}/waseel-plan/{waseelPlanId}")
    PayorPlanDTO getPayorPlanByWaseelPlanId(
            @PathVariable Long payorId,
            @PathVariable String waseelPlanId
    );


    @GetMapping("/api/setup/payor-plan/{id}")
    PayorPlanDTO getPayorPlanById(@PathVariable("id") @NotNull Long payorPlanId);

    @GetMapping("/api/setup/payor-plan/cchi/by-payor/{payorId}/match")
    PayorPlanDTO getPayorPlanByCchiMatch(
            @PathVariable("payorId") Long payorId,
            @RequestParam(required = false) String coverageType,
            @RequestParam(required = false) String networkId,
            @RequestParam(required = false) String policyClassName
    );
}
