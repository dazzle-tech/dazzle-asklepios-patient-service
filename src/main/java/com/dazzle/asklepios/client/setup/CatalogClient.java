package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;


@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface CatalogClient {

    @GetMapping("/api/setup/catalog/{catalogId}/tests")
    List<DiagnosticTestSetupDTO> getTestsByCatalog(@PathVariable Long catalogId);

}