package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.client.WaseelPreAuthorizationClient;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCommunicationResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class WaseelPreAuthorizationService {

    private final WaseelPreAuthorizationClient client;
    private final WaseelApiProperties properties;

    public PreAuthorizationSearchResponse search(Long requestId) {
        return client.searchPreAuthorization(properties.providerId(), requestId);
    }

    public PreAuthorizationCommunicationResponse communicate(PreAuthorizationCommunicationRequest request) {
        return client.sendCommunication(properties.providerId(), request);
    }

    public PreAuthorizationCancelResponse cancel(PreAuthorizationCancelRequest request) {
        return client.cancelPreAuthorization(properties.providerId(), request);
    }
}