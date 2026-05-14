package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.client.WaseelAuthClient;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthResponse;
import org.springframework.stereotype.Service;

@Service
public class WaseelTokenService {

    private final WaseelAuthClient authClient;
    private final WaseelApiProperties properties;

    private String cachedToken;

    public WaseelTokenService(WaseelAuthClient authClient, WaseelApiProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
    }

    public synchronized String getToken() {
        if (cachedToken == null || cachedToken.isBlank()) {
            refreshToken();
        }
        return cachedToken;
    }

    public synchronized String refreshToken() {
        WaseelAuthResponse response = authClient.authenticate(
                new WaseelAuthRequest(properties.username(), properties.password())
        );

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Waseel authentication failed: access_token is missing.");
        }

        this.cachedToken = response.accessToken();
        return cachedToken;
    }

    public synchronized void clearToken() {
        this.cachedToken = null;
    }
}