package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.UomGroupRelationDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "setupServiceClient",
        contextId = "uomGroupClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface UomGroupClient {

    @GetMapping("/api/setup/uom-groups/relations/{groupId}")
    List<UomGroupRelationDTO> getRelations(@PathVariable("groupId") @NotNull Long groupId);
}
