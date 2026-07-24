package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "setupServiceClient",
        contextId = "serviceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface ServiceClient {

    @GetMapping("/api/setup/service/{id}")
    ServiceSetupDTO getServiceDetails(
            @PathVariable("id") Long id
    );
}