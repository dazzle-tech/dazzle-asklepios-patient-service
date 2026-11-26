package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.AmbientScribeAudioRetentionDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
    name = "setupServiceClient",
    contextId = "ambientScribeClient",
    url = "${service.asklepios-setup-service-url}",
    configuration = SetupServiceFeignConfig.class
)
public interface AmbientScribeClient {
    @GetMapping("/api/setup/ambient-scribe/internalJob")
    List<AmbientScribeAudioRetentionDTO> listAudioRetention();
}
