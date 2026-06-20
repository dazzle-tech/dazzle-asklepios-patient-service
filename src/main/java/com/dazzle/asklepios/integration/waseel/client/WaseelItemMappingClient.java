package com.dazzle.asklepios.integration.waseel.client;


import com.dazzle.asklepios.integration.waseel.client.dto.WaseelItemMappingSetupDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "dazzle-asklepios-setup-service",
        contextId = "waseelItemMappingClient"
)
public interface WaseelItemMappingClient {

    @GetMapping("/api/setup/waseel/item-mapping/by-item")
    WaseelItemMappingSetupDTO getMappingByItem(
            @RequestParam("itemType") String itemType,
            @RequestParam("sourceId") Long sourceId
    );
}