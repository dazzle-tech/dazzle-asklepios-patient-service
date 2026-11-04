package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.CdtServiceMappingDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "setupServiceClient",
        contextId = "cdtServiceMappingClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface CdtServiceMappingClient {

    @GetMapping("/api/setup/cdt-service-mappings/by-cdt/{cdtId}")
    List<CdtServiceMappingDTO> getMappingsForCdt(@PathVariable("cdtId") Long cdtId);
}
