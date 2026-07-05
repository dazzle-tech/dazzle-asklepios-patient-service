package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.PreAuthorizationTrack;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.WaseelPreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCommunicationResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import com.dazzle.asklepios.integration.waseel.service.mapper.WaseelCancelReasonMapper;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.repository.PreAuthorizationTrackRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
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
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationTrackRepository preAuthorizationTrackRepository;

    @Transactional
    public PreAuthorizationSearchResponse searchAndUpdate(
            Long preAuthorizationId,
            Long requestId
    ) {
        if (preAuthorizationId == null) {
            throw new IllegalArgumentException("preAuthorizationId is required");
        }

        if (requestId == null) {
            throw new IllegalArgumentException("requestId is required");
        }

        PreAuthorizationRequest preAuth = preAuthorizationRequestRepository
                .findById(preAuthorizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PreAuthorizationRequest not found with id " + preAuthorizationId
                ));

        PreAuthorizationSearchResponse searchResponse = searchFromWaseel(requestId);

        updatePreAuthorizationAfterSearch(preAuth, searchResponse);

        saveSearchTrack(preAuth, requestId, searchResponse);

        return searchResponse;
    }

    private PreAuthorizationSearchResponse searchFromWaseel(Long requestId) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/nphies-rest-external/providers/"
                + properties.providerId()
                + "/external/approval?requestId="
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

    private void updatePreAuthorizationAfterSearch(
            PreAuthorizationRequest preAuth,
            PreAuthorizationSearchResponse searchResponse
    ) {
        if (searchResponse == null) {
            return;
        }

        preAuth.setApprovalRequestId(searchResponse.approvalRequestId());
        preAuth.setApprovalResponseId(searchResponse.approvalResponseId());
        preAuth.setPreAuthRefNo(searchResponse.preAuthRefNo());

        preAuth.setOutcome(searchResponse.outcome());

        preAuth.setStatus(
                firstNonBlank(
                        searchResponse.status(),
                        searchResponse.outcome(),
                        preAuth.getStatus(),
                        "UNKNOWN"
                )
        );

        preAuth.setDisposition(searchResponse.disposition());

        preAuth.setCancelStatus(searchResponse.cancelStatus());
        preAuth.setCancelMessage(searchResponse.cancelResponseReason());

        if (searchResponse.cancelStatus() != null) {
            preAuth.setIsCancelled(Boolean.TRUE);
        }

        if (searchResponse.paymentAmount() != null) {
            preAuth.setTotalNet(searchResponse.paymentAmount());
        }

        preAuth.setSearchResponseJson(toJsonWithoutNulls(searchResponse));

        preAuthorizationRequestRepository.save(preAuth);
    }

    private void saveSearchTrack(
            PreAuthorizationRequest preAuth,
            Long requestId,
            PreAuthorizationSearchResponse searchResponse
    ) {
        PreAuthorizationTrack track = PreAuthorizationTrack.builder()
                .preAuthorization(preAuth)
                .trackType("SEARCH_REFRESH")
                .status(
                searchResponse != null
                        ? firstNonBlank(
                        searchResponse.status(),
                        searchResponse.outcome()
                )
                        : null
        )
                .outcome(searchResponse != null ? searchResponse.outcome() : null)
                .message(searchResponse != null ? searchResponse.cancelResponseReason() : null)
                .disposition(searchResponse != null ? searchResponse.disposition() : null)
                .transactionId(searchResponse != null ? searchResponse.providertransactionlogId() : null)
                .approvalRequestId(searchResponse != null ? searchResponse.approvalRequestId() : requestId)
                .approvalResponseId(searchResponse != null ? searchResponse.approvalResponseId() : null)
                .requestJson(toJsonWithoutNulls(java.util.Map.of("requestId", requestId)))
                .responseJson(toJsonWithoutNulls(searchResponse))
                .build();

        preAuthorizationTrackRepository.save(track);
    }
    public PreAuthorizationCommunicationResponse communicate(
            PreAuthorizationCommunicationRequest request
    ) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/poll-management/providers/"
                + properties.providerId()
                + "/communication";

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

    @Transactional
    public PreAuthorizationSearchResponse cancel(PreAuthorizationCancelRequest request) {
        if (request.preAuthorizationId() == null) {
            throw new IllegalArgumentException("preAuthorizationId is required");
        }

        if (request.approvalRequestId() == null) {
            throw new IllegalArgumentException("approvalRequestId is required");
        }

        PreAuthorizationRequest preAuth = preAuthorizationRequestRepository
                .findById(request.preAuthorizationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "PreAuthorizationRequest not found with id " + request.preAuthorizationId()
                ));

        PreAuthorizationCancelResponse cancelResponse = sendCancelToWaseel(request);

        sleepBeforeRefresh();

        PreAuthorizationSearchResponse searchResponse = searchFromWaseel(request.approvalRequestId());
        updatePreAuthorizationAfterCancel(
                preAuth,
                request,
                cancelResponse,
                searchResponse
        );

        saveCancelRefreshTrack(
                preAuth,
                request,
                cancelResponse,
                searchResponse
        );

        return searchResponse;
    }

    private PreAuthorizationCancelResponse sendCancelToWaseel(
            PreAuthorizationCancelRequest request
    ) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/cancel/request";

        WaseelPreAuthorizationCancelRequest waseelRequest =
                new WaseelPreAuthorizationCancelRequest(
                        String.valueOf(request.approvalRequestId()),
                        WaseelCancelReasonMapper.toWaseelCode(request.cancelReason())
                );

        String jsonBody = toJsonWithoutNulls(waseelRequest);

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

    private void updatePreAuthorizationAfterCancel(
            PreAuthorizationRequest preAuth,
            PreAuthorizationCancelRequest request,
            PreAuthorizationCancelResponse cancelResponse,
            PreAuthorizationSearchResponse searchResponse
    ) {
        preAuth.setIsCancelled(Boolean.TRUE);
        preAuth.setCancelReason(request.cancelReason());

        if (cancelResponse != null) {
            preAuth.setCancelOutcome(cancelResponse.outcome());
            preAuth.setCancelMessage(cancelResponse.message());
            preAuth.setCancelResponseJson(toJsonWithoutNulls(cancelResponse));

            if (cancelResponse.approvalRequestId() != null) {
                preAuth.setApprovalRequestId(cancelResponse.approvalRequestId());
            }
        }

        if (searchResponse != null) {

            preAuth.setApprovalRequestId(searchResponse.approvalRequestId());
            preAuth.setApprovalResponseId(searchResponse.approvalResponseId());
            preAuth.setPreAuthRefNo(searchResponse.preAuthRefNo());

            preAuth.setOutcome(searchResponse.outcome());

            preAuth.setStatus(
                    firstNonBlank(
                            searchResponse.status(),
                            searchResponse.outcome(),
                            preAuth.getStatus(),
                            "UNKNOWN"
                    )
            );

            preAuth.setDisposition(searchResponse.disposition());

            preAuth.setCancelStatus(searchResponse.cancelStatus());
            preAuth.setCancelMessage(searchResponse.cancelResponseReason());

            preAuth.setSearchResponseJson(toJsonWithoutNulls(searchResponse));

            if (searchResponse.paymentAmount() != null) {
                preAuth.setTotalNet(searchResponse.paymentAmount());
            }
        }

        preAuthorizationRequestRepository.save(preAuth);
    }

    private void saveCancelRefreshTrack(
            PreAuthorizationRequest preAuth,
            PreAuthorizationCancelRequest request,
            PreAuthorizationCancelResponse cancelResponse,
            PreAuthorizationSearchResponse searchResponse
    ) {
        PreAuthorizationTrack track = PreAuthorizationTrack.builder()
                .preAuthorization(preAuth)
                .trackType("CANCEL_REFRESH")
                .status(
                        searchResponse != null
                                ? firstNonBlank(
                                searchResponse.status(),
                                searchResponse.outcome()
                        )
                                : null
                ).status(
                        searchResponse != null
                                ? firstNonBlank(
                                searchResponse.status(),
                                searchResponse.outcome()
                        )
                                : null
                )                .outcome(searchResponse != null ? searchResponse.outcome() : null)
                .message(searchResponse != null ? searchResponse.cancelResponseReason() : null)
                .disposition(searchResponse != null ? searchResponse.disposition() : null)
                .transactionId(searchResponse != null ? searchResponse.providertransactionlogId() : null)
                .approvalRequestId(searchResponse != null ? searchResponse.approvalRequestId() : request.approvalRequestId())
                .approvalResponseId(searchResponse != null ? searchResponse.approvalResponseId() : null)
                .requestJson(toJsonWithoutNulls(request))
                .responseJson(toJsonWithoutNulls(searchResponse != null ? searchResponse : cancelResponse))
                .build();

        preAuthorizationTrackRepository.save(track);
    }

    private void sleepBeforeRefresh() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return null;
    }
}