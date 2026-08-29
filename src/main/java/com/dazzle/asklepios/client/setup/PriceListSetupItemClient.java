package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PriceListItemWaseelCodesDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PriceListSetupItemClient {

    @GetMapping("/api/setup/price-list-setup-items/waseel-codes")
    PriceListItemWaseelCodesDTO getInsuranceWaseelCodes(
            @RequestParam("itemType") String itemType,
            @RequestParam("sourceId") Long sourceId,
            @RequestParam(value = "facilityId", required = false) Long facilityId
    );
}
