package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.FinancialDocumentNumberRequest;
import com.dazzle.asklepios.client.setup.dto.FinancialDocumentNumberResponse;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface FinancialDocumentNumberingClient {

    @PostMapping("/api/setup/internal/financial-document-number/next")
    FinancialDocumentNumberResponse generateNextNumber(
            @RequestBody FinancialDocumentNumberRequest request
    );
}
