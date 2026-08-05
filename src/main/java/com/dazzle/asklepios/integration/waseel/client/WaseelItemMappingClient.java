package com.dazzle.asklepios.integration.waseel.client;


import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.integration.waseel.client.dto.WaseelItemMappingSetupDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface WaseelItemMappingClient {

    @GetMapping("/api/setup/waseel/item-mapping/by-item")
    WaseelItemMappingSetupDTO getMappingByItem(
            @RequestParam("itemType") String itemType,
            @RequestParam("sourceId") Long sourceId
    );

    @GetMapping("/api/setup/waseel/item-mapping/{itemType}/{sourceId}/requires-preauth")
    Boolean requiresPreauth(
            @PathVariable("itemType") BillingItemTypes itemType,
            @PathVariable("sourceId") Long sourceId
    );
}