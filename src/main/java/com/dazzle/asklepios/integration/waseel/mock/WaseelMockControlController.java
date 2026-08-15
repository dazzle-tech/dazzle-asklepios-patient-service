package com.dazzle.asklepios.integration.waseel.mock;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control endpoints for the in-memory Waseel pre-authorization mock.
 */
@RestController
@RequestMapping("/api/patient/internal/waseel/mock")
@ConditionalOnProperty(name = "waseel.api.mock-enabled", havingValue = "true")
@RequiredArgsConstructor
public class WaseelMockControlController {

    private final WaseelPreAuthorizationMockService mockService;

    @GetMapping("/status")
    public WaseelPreAuthorizationMockStatusResponse status() {
        return WaseelPreAuthorizationMockStatusResponse.builder()
                .enabled(mockService.isEnabled())
                .defaultScenario(mockService.defaultScenario())
                .activeRequests(mockService.activeScenarios())
                .build();
    }

    @PutMapping("/scenario")
    public WaseelPreAuthorizationMockStatusResponse setDefaultScenario(
            @RequestParam("scenario") String scenario
    ) {
        mockService.setDefaultScenario(WaseelPreAuthorizationMockScenario.fromConfig(scenario));
        return status();
    }

    @PutMapping("/pre-authorizations/{approvalRequestId}/scenario")
    public WaseelPreAuthorizationMockStatusResponse setRequestScenario(
            @PathVariable Long approvalRequestId,
            @RequestParam("scenario") String scenario
    ) {
        mockService.setScenarioForRequest(
                approvalRequestId,
                WaseelPreAuthorizationMockScenario.fromConfig(scenario)
        );
        return status();
    }
}
