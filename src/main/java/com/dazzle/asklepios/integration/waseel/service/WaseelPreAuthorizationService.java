
        package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCommunicationResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaseelPreAuthorizationService {

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;
    private final ObjectMapper objectMapper;

    public PreAuthorizationSearchResponse search(Long requestId) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/search/"
                + requestId;

        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(token));

        try {
            log.info("========== WASEEL PRE-AUTH SEARCH REQUEST ==========");
            log.info("URL: {}", url);
            log.info("====================================================");

            ResponseEntity<PreAuthorizationSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    PreAuthorizationSearchResponse.class
            );

            log.info("========== WASEEL PRE-AUTH SEARCH RESPONSE ==========");
            log.info("Status Code: {}", response.getStatusCode());
            log.info("Response Body: {}", toJsonWithoutNulls(response.getBody()));
            log.info("=====================================================");

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("PRE-AUTH SEARCH", ex, null);
            throw ex;
        }
    }

    public PreAuthorizationCommunicationResponse communicate(PreAuthorizationCommunicationRequest request) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/communication/request";

        String jsonBody = toJsonWithoutNulls(request);

        HttpEntity<String> entity = new HttpEntity<>(
                jsonBody,
                buildHeaders(token)
        );

        try {
            log.info("========== WASEEL PRE-AUTH COMMUNICATION REQUEST ==========");
            log.info("URL: {}", url);
            log.info("Request Body: {}", jsonBody);
            log.info("===========================================================");

            ResponseEntity<PreAuthorizationCommunicationResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    PreAuthorizationCommunicationResponse.class
            );

            log.info("========== WASEEL PRE-AUTH COMMUNICATION RESPONSE ==========");
            log.info("Status Code: {}", response.getStatusCode());
            log.info("Response Body: {}", toJsonWithoutNulls(response.getBody()));
            log.info("============================================================");

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("PRE-AUTH COMMUNICATION", ex, jsonBody);
            throw ex;
        }
    }

    public PreAuthorizationCancelResponse cancel(PreAuthorizationCancelRequest request) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/cancel/request";

        String jsonBody = toJsonWithoutNulls(request);

        HttpEntity<String> entity = new HttpEntity<>(
                jsonBody,
                buildHeaders(token)
        );

        try {
            log.info("========== WASEEL PRE-AUTH CANCEL REQUEST ==========");
            log.info("URL: {}", url);
            log.info("Request Body: {}", jsonBody);
            log.info("====================================================");

            ResponseEntity<PreAuthorizationCancelResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    PreAuthorizationCancelResponse.class
            );

            log.info("========== WASEEL PRE-AUTH CANCEL RESPONSE ==========");
            log.info("Status Code: {}", response.getStatusCode());
            log.info("Response Body: {}", toJsonWithoutNulls(response.getBody()));
            log.info("=====================================================");

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("PRE-AUTH CANCEL", ex, jsonBody);
            throw ex;
        }
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");
        return headers;
    }

    private void logWaseelError(
            String operation,
            HttpStatusCodeException ex,
            String jsonBody
    ) {
        log.error("========== WASEEL {} ERROR ==========", operation);
        log.error("Status Code: {}", ex.getStatusCode());
        log.error("Response Body: {}", ex.getResponseBodyAsString());
        log.error("Response Headers: {}", ex.getResponseHeaders());

        if (jsonBody != null) {
            log.error("Request Body: {}", jsonBody);
        }

        log.error("====================================", ex);
    }

    private String toJsonWithoutNulls(Object value) {
        if (value == null) {
            return "{}";
        }

        try {
            ObjectMapper mapper = objectMapper.copy();

            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, false);

            return mapper.writeValueAsString(value);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize Waseel pre-authorization request",
                    ex
            );
        }
    }
}

