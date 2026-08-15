package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
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
public class WaseelApprovalService {

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final WaseelPreAuthorizationMockService mockService;

    public ApprovalResponse requestApproval(WaseelApprovalRequest request) {
        if (mockService.isEnabled()) {
            return mockService.requestApproval(request);
        }

        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/request";

        String jsonBody = toJsonWithoutNulls(request);

        HttpEntity<String> entity = new HttpEntity<>(
                jsonBody,
                buildHeaders(token)
        );

        try {
            log.info("========== WASEEL APPROVAL REQUEST ==========");
            log.info("URL: {}", url);
            log.info("Request Body: {}", jsonBody);
            log.info("============================================");

            ResponseEntity<ApprovalResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApprovalResponse.class
            );

            log.info("========== WASEEL APPROVAL RESPONSE ==========");
            log.info("Status Code: {}", response.getStatusCode());
            log.info("Response Body: {}", toJsonWithoutNulls(response.getBody()));
            log.info("=============================================");

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("APPROVAL", ex, jsonBody);
            throw ex;
        }
    }

    public ApprovalResponse cancelApproval(ApprovalCancelRequest request) {
        if (mockService.isEnabled()) {
            return mockService.cancelApproval(request);
        }

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
            ResponseEntity<ApprovalResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApprovalResponse.class
            );

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("CANCEL", ex, jsonBody);
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
        log.error("Request Body: {}", jsonBody);
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
                    "Failed to serialize Waseel approval request",
                    ex
            );
        }
    }
}