package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import com.dazzle.asklepios.domain.enumeration.SystemConfigKey;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "setupServiceClient", url = "${service.asklepios-setup-service-url}", configuration = SetupServiceFeignConfig.class)
public interface SystemConfigurationClient {
    @GetMapping("/api/setup/system-configurations/{key}/resolved-value")
    String getResolvedValue(@PathVariable("key") SystemConfigKey key);
}
