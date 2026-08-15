package com.dazzle.asklepios.integration.waseel.mock;

import lombok.Builder;

import java.util.Map;

@Builder
public record WaseelPreAuthorizationMockStatusResponse(
        boolean enabled,
        WaseelPreAuthorizationMockScenario defaultScenario,
        Map<Long, WaseelPreAuthorizationMockScenario> activeRequests
) {}
