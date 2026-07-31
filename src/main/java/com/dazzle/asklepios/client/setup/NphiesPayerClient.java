package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface NphiesPayerClient {

    @GetMapping("/api/setup/nphies-payers/by-nphies-id/{nphiesId}")
    List<NphiesPayerDTO> getNphiesPayersByNphiesId(
            @PathVariable("nphiesId") String nphiesId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1") int size
    );
}
