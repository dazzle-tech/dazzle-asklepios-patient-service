package com.dazzle.asklepios.integration.waseel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Waseel API settings.
 *
 * <p>When {@code mock-enabled=true}, pre-authorization HTTP calls (submit / search /
 * communication / cancel) are answered locally by
 * {@link com.dazzle.asklepios.integration.waseel.service.WaseelPreAuthorizationMockService}
 * instead of hitting Waseel. Business services stay unchanged.
 *
 * <p>{@code mock-pre-auth-status} controls the search outcome shape:
 * {@code approved}, {@code rejected}, {@code partial}, or {@code pended}.
 */
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
        String mockPreAuthStatus
) {
    public boolean isMockEnabled() {
        return Boolean.TRUE.equals(mockEnabled);
    }
}