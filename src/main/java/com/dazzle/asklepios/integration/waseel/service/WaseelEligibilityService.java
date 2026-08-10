package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaseelEligibilityService {

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;
    private final ObjectMapper objectMapper;

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

        String jsonBody = toJsonWithoutNulls(request);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders(token));

        try {
            log.info("========== WASEEL ELIGIBILITY REQUEST ==========");
            log.info("URL: {}", url);
            log.info("Request Body: {}", jsonBody);
            log.info("==============================================");

            ResponseEntity<EligibilityResponse> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            EligibilityResponse.class
                    );

            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("========== WASEEL ELIGIBILITY ERROR ==========");
            log.error("Status Code: {}", ex.getStatusCode());
            log.error("Response Body: {}", ex.getResponseBodyAsString());
            log.error("Request Body: {}", jsonBody);
            log.error("============================================", ex);
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
            throw new IllegalStateException("Failed to serialize Waseel eligibility request", ex);
        }
    }
}
