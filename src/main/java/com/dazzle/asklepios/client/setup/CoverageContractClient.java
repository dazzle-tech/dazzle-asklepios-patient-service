package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.CoverageContractResolveDtos;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "setupServiceClient",
        contextId = "coverageContractClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface CoverageContractClient {

    @PostMapping("/api/setup/coverage-contracts/resolve")
    CoverageContractResolveDtos.Response resolve(
            @RequestBody CoverageContractResolveDtos.Request request
    );
}
