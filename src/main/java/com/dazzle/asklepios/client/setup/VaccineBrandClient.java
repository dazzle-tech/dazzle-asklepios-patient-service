package com.dazzle.asklepios.client.setup;


import com.dazzle.asklepios.client.setup.dto.VaccineBrandSetupDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface VaccineBrandClient {

    @GetMapping("/api/setup/vaccine-brands/{id}")
    ResponseEntity<Void> existsVaccineBrand(@PathVariable Long id);

    @GetMapping("/api/setup/vaccine-brands/{id}")
    VaccineBrandSetupDTO getVaccineBrand(@PathVariable("id") Long id);

    }
