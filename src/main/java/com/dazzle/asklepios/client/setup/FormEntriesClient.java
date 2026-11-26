package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.FormEntryDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface FormEntriesClient {

    @GetMapping("/api/setup/form-entries/by-encounter/{encounterId}")
    List<FormEntryDTO> listByEncounter(
            @PathVariable("encounterId") Long encounterId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "1") int size
    );
}
