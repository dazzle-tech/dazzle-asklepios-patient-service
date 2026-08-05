package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaseelClaimService {

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Returns existing upload IDs when the extraction/upload name is already taken.
     * An empty list means the name is available.
     */
    public List<Long> checkExtractionName(String extractionName) {
        String token = tokenService.getToken();

        String url = UriComponentsBuilder
                .fromHttpUrl(properties.baseUrl()
                        + "/upload-v2/providers/"
                        + properties.providerId()
                        + "/claims/check/extractionName")
                .queryParam("extractionName", extractionName)
                .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(token));

        try {
            log.info("========== WASEEL CLAIM CHECK NAME ==========");
            log.info("URL: {}", url);
            log.info("============================================");

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getBody() == null || response.getBody().isBlank()) {
                return List.of();
            }

            List<Long> ids = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<>() {}
            );
            return ids == null ? List.of() : ids;

        } catch (HttpStatusCodeException ex) {
            logWaseelError("CLAIM_CHECK_NAME", ex, null);
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse claim extraction name check response", ex);
        }
    }

    public WaseelClaimUploadResponse uploadClaims(WaseelClaimUploadRequest request) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/upload-v2/providers/"
                + properties.providerId()
                + "/claim/multi-upload-compressed";

        String jsonBody = toJsonWithoutNulls(request);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders(token));

        try {
            log.info("========== WASEEL CLAIM UPLOAD REQUEST ==========");
            log.info("URL: {}", url);
            log.info("Request Body: {}", jsonBody);
            log.info("=================================================");

            ResponseEntity<WaseelClaimUploadResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    WaseelClaimUploadResponse.class
            );

            log.info("========== WASEEL CLAIM UPLOAD RESPONSE ==========");
            log.info("Status Code: {}", response.getStatusCode());
            log.info("Response Body: {}", toJsonWithoutNulls(response.getBody()));
            log.info("==================================================");

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("CLAIM_UPLOAD", ex, jsonBody);
            throw ex;
        }
    }

    public WaseelClaimUploadResponse getUploadSummary(Long uploadId) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/upload-v2/providers/"
                + properties.providerId()
                + "/claim/"
                + uploadId;

        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(token));

        try {
            ResponseEntity<WaseelClaimUploadResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    WaseelClaimUploadResponse.class
            );
            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("CLAIM_SUMMARY", ex, null);
            throw ex;
        }
    }

    /**
     * Loads claim transaction details from Waseel (status, disposition, validation errors).
     */
    public String searchClaimByProvClaimNo(String provClaimNo) {
        String token = tokenService.getToken();

        String url = UriComponentsBuilder
                .fromHttpUrl(properties.baseUrl()
                        + "/nphies-rest-external/providers/"
                        + properties.providerId()
                        + "/external/claim")
                .queryParam("provClaimNo", provClaimNo)
                .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(token));

        try {
            log.info("========== WASEEL CLAIM SEARCH ==========");
            log.info("URL: {}", url);
            log.info("=========================================");

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError("CLAIM_SEARCH", ex, null);
            throw ex;
        }
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");
        return headers;
    }

    private void logWaseelError(String operation, HttpStatusCodeException ex, String jsonBody) {
        log.error("========== WASEEL {} ERROR ==========", operation);
        log.error("Status Code: {}", ex.getStatusCode());
        log.error("Response Body: {}", ex.getResponseBodyAsString());
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
            throw new IllegalStateException("Failed to serialize Waseel claim request", ex);
        }
    }
}
