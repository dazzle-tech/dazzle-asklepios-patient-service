package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PayorPlanCoverageClassDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PayorPlanCoverageClassClient {

    @GetMapping("/api/setup/payor-plan/{planId}/coverage-class/active")
    List<PayorPlanCoverageClassDTO> getActiveCoverageClassesByPlan(
            @PathVariable Long planId
    );
}