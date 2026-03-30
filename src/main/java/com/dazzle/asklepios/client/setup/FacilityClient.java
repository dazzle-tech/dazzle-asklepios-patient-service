package com.dazzle.asklepios.client.setup;


import com.dazzle.asklepios.client.setup.vm.FacilityVM;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface FacilityClient {

    @GetMapping("/api/setup/facility/{id}")
    FacilityVM getFacility(@PathVariable("id") Long id);
}
