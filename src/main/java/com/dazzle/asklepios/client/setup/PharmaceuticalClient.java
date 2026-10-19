package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PharmaceuticalDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "setupServiceClient",
        contextId = "pharmaceuticalClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PharmaceuticalClient {

    @GetMapping("/api/setup/pharmaceuticals/{id}")
    PharmaceuticalDTO getPharmaceutical(@PathVariable("id") @NotNull Long pharmaceuticalId);
}
