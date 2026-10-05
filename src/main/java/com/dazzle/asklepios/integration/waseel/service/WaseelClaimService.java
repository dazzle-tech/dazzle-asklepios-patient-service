package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimValidationError;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
     * Claims Waseel marked not accepted inside one upload, keyed by provider claim number.
     * An empty map means the upload has no not-accepted claim rows.
     */
    public Map<String, List<ClaimValidationError>> findNotAcceptedClaims(Long uploadId) {
        Map<String, List<ClaimValidationError>> rejected = new LinkedHashMap<>();
        int page = 0;
        boolean last = false;
        while (!last) {
            JsonNode body = getJson(uploadDetailsUrl(uploadId, null, page));
            JsonNode content = body == null ? null : body.get("content");
            if (content != null && content.isArray()) {
                for (JsonNode field : content) {
                    if (!isNotAccepted(field)) {
                        continue;
                    }
                    String fieldName = text(field.get("fieldName"));
                    if (fieldName == null) {
                        continue;
                    }
                    collectFieldErrors(uploadId, fieldName, rejected);
                }
            }
            last = body == null || body.path("last").asBoolean(true);
            page++;
        }
        return rejected;
    }

    private void collectFieldErrors(
            Long uploadId,
            String fieldName,
            Map<String, List<ClaimValidationError>> rejected
    ) {
        int page = 0;
        boolean last = false;
        while (!last) {
            JsonNode body = getJson(uploadDetailsUrl(uploadId, fieldName, page));
            JsonNode content = body == null ? null : body.get("content");
            if (content != null && content.isArray()) {
                for (JsonNode row : content) {
                    String provClaimNo = text(row.get("providerClaimNo"));
                    String message = text(row.get("errorDescription"));
                    if (provClaimNo == null || message == null) {
                        continue;
                    }
                    rejected.computeIfAbsent(provClaimNo.toUpperCase(Locale.ROOT), key -> new ArrayList<>())
                            .add(new ClaimValidationError(
                                    firstNonBlank(text(row.get("errorCode")), text(row.get("fieldName")), "Waseel"),
                                    message,
                                    firstNonBlank(text(row.get("fieldName")), "Errors & Warnings")
                            ));
                }
            }
            last = body == null || body.path("last").asBoolean(true);
            page++;
        }
    }

    private String uploadDetailsUrl(Long uploadId, String fieldName, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.baseUrl())
                .path("/provider-nphies-claim-search/providers/{providerId}/history/{uploadId}/details");
        if (fieldName != null) {
            builder.path("/{fieldName}");
        }
        builder.queryParam("page", page).queryParam("size", 50);
        if (fieldName == null) {
            return builder.buildAndExpand(properties.providerId(), uploadId).encode().toUriString();
        }
        return builder.buildAndExpand(properties.providerId(), uploadId, fieldName).encode().toUriString();
    }

    private JsonNode getJson(String url) {
        String token = tokenService.getToken();
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(token));
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            if (response.getBody() == null || response.getBody().isBlank()) {
                return null;
            }
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException ex) {
            logWaseelError("CLAIM_UPLOAD_DETAILS", ex, null);
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read Waseel upload claim details", ex);
        }
    }

    private boolean isNotAccepted(JsonNode field) {
        String subStatus = text(field == null ? null : field.get("subStatus"));
        if (subStatus == null) {
            return false;
        }
        String normalized = subStatus.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        return normalized.contains("notaccepted")
                || normalized.contains("rejected")
                || normalized.contains("denied");
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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
