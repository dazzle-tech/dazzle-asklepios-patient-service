package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class WaseelEligibilityService {

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;

    public WaseelEligibilityService(
            RestTemplate restTemplate,
            WaseelTokenService tokenService,
            WaseelApiProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.properties = properties;
    }
    public EligibilityResponse requestEligibility(EligibilityRequest request) {
        try {
            return doRequestEligibility(request, tokenService.getToken());
        } catch (HttpClientErrorException.Unauthorized ex) {
            tokenService.clearToken();
            return doRequestEligibility(request, tokenService.getToken());
        }
    }

    private EligibilityResponse doRequestEligibility(EligibilityRequest request, String token) {

        String url =
                properties.baseUrl()
                        + "/eligibilities/providers/"
                        + properties.providerId()
                        + "/request";

        if (token != null && token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");

        HttpEntity<EligibilityRequest> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<EligibilityResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        EligibilityResponse.class
                );

        return response.getBody();
    }
}

