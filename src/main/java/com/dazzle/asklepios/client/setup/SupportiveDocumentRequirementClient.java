package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PayorSupportiveDocumentRequirementDTO;
import com.dazzle.asklepios.client.setup.dto.SupportiveDocumentRequirementCatalogDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Phase 1 setup-service APIs for Payor supportive-document requirements.
 * Dedicated client (same pattern as {@link PriceListSetupClient}) — not Payor CRUD.
 */
@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface SupportiveDocumentRequirementClient {

    @GetMapping("/api/setup/supportive-document-requirements")
    List<SupportiveDocumentRequirementCatalogDTO> getCatalog();

    @GetMapping("/api/setup/payor/{payorId}/supportive-document-requirements")
    PayorSupportiveDocumentRequirementDTO getByPayorId(@PathVariable("payorId") @NotNull Long payorId);
}
