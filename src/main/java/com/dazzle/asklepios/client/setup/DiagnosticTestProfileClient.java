package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)

public interface DiagnosticTestProfileClient {
    @GetMapping("/api/setup/diagnostic-test-profiles/{id}/result-type")
    TestResultType getResultTypeByProfileTestIdInternal(@PathVariable("id") Long id);

    @GetMapping("/api/setup/diagnostic-test-profiles/internal/by-test/{testId}/lab-profile-ids")
    List<Long> getTestProfilesIdsByTestId(@PathVariable("testId") Long testId);
}
