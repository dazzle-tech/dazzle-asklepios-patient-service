package com.dazzle.asklepios.integration.waseel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waseel.api")
public record WaseelApiProperties(
        String baseUrl,
        String username,
        String password,
        String providerId,
        Integer timeoutSeconds
) {}