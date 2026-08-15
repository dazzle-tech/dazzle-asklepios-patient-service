package com.dazzle.asklepios.integration.waseel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waseel.api")
public record WaseelApiProperties(
        String baseUrl,
        String username,
        String password,
        String providerId,
        String nphiesId,
        String systemType,
        Integer timeoutSeconds,
        Boolean mockEnabled,
        String mockScenario
) {
    public boolean isMockEnabled() {
        return Boolean.TRUE.equals(mockEnabled);
    }
}