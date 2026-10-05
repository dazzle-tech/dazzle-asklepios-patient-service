package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.BedDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "setupServiceClient",
        contextId = "bedClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface BedClient {

    @GetMapping("/api/setup/bed/{id}")
    ResponseEntity<Void> existsBed(@PathVariable("id") @NotNull Long bedId);

    @PostMapping("/api/setup/bed/{id}/mark-in-cleaning")
    BedDTO markAsInCleaning(@PathVariable("id") @NotNull Long bedId);
}
