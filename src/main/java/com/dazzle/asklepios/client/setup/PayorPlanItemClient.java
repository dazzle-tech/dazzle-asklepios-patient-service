package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "payorPlanItemClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PayorPlanItemClient {

    @GetMapping("/api/setup/payor-plan-items/procedure/{procedureId}/requires-pre-authorization")
    Boolean requiresPreAuthorizationForProcedure(@PathVariable("procedureId") Long procedureId);

    @GetMapping("/api/setup/payor-plan-items/service/{serviceId}/requires-pre-authorization")
    Boolean requiresPreAuthorizationForService(@PathVariable("serviceId") Long serviceId);

    @GetMapping("/api/setup/payor-plan-items/diagnostic-test/{diagnosticTestId}/pre-authorization-required")
    Boolean requiresPreAuthorizationForDiagnosticTest(
            @PathVariable("diagnosticTestId") Long diagnosticTestId
    );
    @GetMapping("/api/setup/payor-plan-items/brand-medication/{brandMedicationId}/pre-authorization-required")
    Boolean requiresPreAuthorizationForMedication(
            @PathVariable("brandMedicationId") Long brandMedicationId
    );
}