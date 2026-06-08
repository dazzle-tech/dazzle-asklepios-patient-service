package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.client.WaseelAuthClient;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class WaseelTokenService {

    private final WaseelAuthClient authClient;
    private final WaseelApiProperties properties;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public WaseelTokenService(WaseelAuthClient authClient, WaseelApiProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
    }

    public synchronized String getToken() {
        if (cachedToken == null || cachedToken.isBlank() || isTokenExpired()) {
            refreshToken();
        }

        return stripBearer(cachedToken);
    }

    public synchronized String refreshToken() {
        WaseelAuthResponse response = authClient.authenticate(
                new WaseelAuthRequest(properties.username(), properties.password())
        );

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Waseel authentication failed: access_token is missing.");
        }

        this.cachedToken = stripBearer(response.accessToken());


        this.tokenExpiresAt = Instant.now().plusSeconds(50 * 60);

        return cachedToken;
    }

    public synchronized void clearToken() {
        this.cachedToken = null;
        this.tokenExpiresAt = null;
    }

    private boolean isTokenExpired() {
        if (tokenExpiresAt == null) {
            return true;
        }

        return Instant.now().isAfter(tokenExpiresAt.minusSeconds(60));
    }

    private String stripBearer(String token) {
        if (token == null) {
            return null;
        }

        String cleaned = token.trim();

        if (cleaned.toLowerCase().startsWith("bearer ")) {
            return cleaned.substring(7).trim();
        }

        return cleaned;
    }
}