package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PrescriptionInstructionDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "setupServiceClient",
        contextId = "prescriptionInstructionClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PrescriptionInstructionClient {

    @GetMapping("/api/setup/prescription-instruction/{id}")
    PrescriptionInstructionDTO getPrescriptionInstruction(@PathVariable("id") @NotNull Long prescriptionInstructionId);
}
