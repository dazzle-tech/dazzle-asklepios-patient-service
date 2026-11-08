package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.TpaResolutionDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface PayorClient {
    @GetMapping("/api/setup/payor/{id}")
    ResponseEntity<Void> existsPayor(@PathVariable("id") @NotNull Long payorId );

    @GetMapping("/api/setup/payor/{id}")
    PayorDTO getPayorById(@PathVariable("id") @NotNull Long payorId);

    @GetMapping("/api/setup/payor/cchi/by-nphies/{nphiesId}")
    PayorDTO getPayorByNphiesId(@PathVariable String nphiesId);

    @PostMapping("/api/setup/payor/ensure-from-nphies/{nphiesId}")
    PayorDTO ensurePayorFromNphiesId(@PathVariable("nphiesId") String nphiesId);

    @GetMapping("/api/setup/tpa-definitions/resolve")
    TpaResolutionDTO resolveTpa(
            @RequestParam(value = "payerNphiesId", required = false) String payerNphiesId,
            @RequestParam(value = "policyNumber", required = false) String policyNumber,
            @RequestParam(value = "preferredTpaNphiesId", required = false) String preferredTpaNphiesId
    );
}
