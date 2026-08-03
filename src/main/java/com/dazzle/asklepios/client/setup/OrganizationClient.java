package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.OrganizationDefinitionDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "setupServiceClient", url = "${service.asklepios-setup-service-url}", configuration = SetupServiceFeignConfig.class)
public interface OrganizationClient {
    @GetMapping("/api/setup/organization-definition")
    List<OrganizationDefinitionDTO> getOrganization();
}
