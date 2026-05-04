package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "setupServiceClient", url = "${service.asklepios-setup-service-url}", configuration = SetupServiceFeignConfig.class)
public interface ProcedureClient {

    @GetMapping("/api/setup/procedure/{id}")
    ProcedureSetupDTO getProcedure(@PathVariable("id") Long id);
}
