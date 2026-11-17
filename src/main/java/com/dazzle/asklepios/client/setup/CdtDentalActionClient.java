package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.CdtDentalActionDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "setupServiceClient",
        contextId = "cdtDentalActionClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface CdtDentalActionClient {

    /** Setup names the path variable "cdtCode", but it carries the CDT code id. */
    @GetMapping("/api/setup/cdt-dental-action/by-code/{cdtCodeId}")
    List<CdtDentalActionDTO> getDentalActionsForCdt(@PathVariable("cdtCodeId") Long cdtCodeId);
}
