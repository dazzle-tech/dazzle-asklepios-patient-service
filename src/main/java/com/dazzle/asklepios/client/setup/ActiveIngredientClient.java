package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.ActiveIngredientDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "setupServiceClient", url = "${service.asklepios-setup-service-url}", configuration = SetupServiceFeignConfig.class)
public interface ActiveIngredientClient {
    @GetMapping("/api/setup/active-ingredients/{id}")
    ResponseEntity<Void> existsActiveIngredient(@PathVariable("id") @NotNull Long activeIngredientId);

    @GetMapping("/api/setup/active-ingredients/{id}")
    ActiveIngredientDTO getActiveIngredient(@PathVariable("id") @NotNull Long activeIngredientId);
}
