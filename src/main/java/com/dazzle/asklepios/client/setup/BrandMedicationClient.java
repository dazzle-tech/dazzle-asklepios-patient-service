package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.BrandMedicationDTO;
import com.dazzle.asklepios.client.setup.dto.BrandMedicationSetupDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "setupServiceClient",
        contextId = "brandMedicationClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface BrandMedicationClient {

    @GetMapping("/api/setup/brand-medication/{id}")
    ResponseEntity<Void> existsBrandMedication(
            @PathVariable("id") @NotNull Long brandMedicationId
    );

    @GetMapping("/api/setup/brand-medication/{id}")
    BrandMedicationSetupDTO getBrandMedication(
            @PathVariable("id") @NotNull Long brandMedicationId
    );

    @PostMapping("/api/setup/brand-medication/by-active-ids")
    List<BrandMedicationSetupDTO> getByActiveIds(@RequestBody List<Long> activeIngredientIds);

    }
