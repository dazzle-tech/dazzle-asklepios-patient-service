package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PreAuthorizationAttachment;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.PreAuthorizationTrack;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.WaseelPreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCommunicationResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import com.dazzle.asklepios.integration.waseel.service.mapper.WaseelCancelReasonMapper;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.repository.PreAuthorizationTrackRepository;
import com.dazzle.asklepios.service.PreAuthorizationAttachmentService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;
    private final PreAuthorizationAttachmentService preAuthorizationAttachmentService;
    private final WaseelPreAuthorizationMockService mockService;

    @Transactional
    public PreAuthorizationSearchResponse searchAndUpdate(
            Long preAuthorizationId,
            Long requestId
    ) {
        if (requestId == null) {
            throw new BadRequestAlertException(
                    "requestId is required",
                    "preAuthorization",
                    "requestId.required"
            );
        }

        PreAuthorizationRequest preAuth = resolvePreAuthorization(preAuthorizationId, requestId);

        Map<String, Object> beforeState = snapshotPreAuthorization(preAuth);

        PreAuthorizationSearchResponse searchResponse = searchFromWaseel(requestId);

        updatePreAuthorizationAfterSearch(preAuth, searchResponse);
        updateItemsAfterSearch(preAuth, searchResponse);

        Map<String, Object> afterState = snapshotPreAuthorization(preAuth);
        saveSearchTrack(preAuth, requestId, beforeState, afterState, searchResponse);

        return searchResponse;
    }

    private PreAuthorizationRequest resolvePreAuthorization(
            Long preAuthorizationId,
            Long requestId
    ) {
        if (preAuthorizationId != null) {
            return preAuthorizationRequestRepository
                    .findById(preAuthorizationId)
                    .orElseThrow(() -> new BadRequestAlertException(
                            "PreAuthorizationRequest not found with id " + preAuthorizationId,
                            "preAuthorization",
                            "notfound"
                    ));
        }

        return preAuthorizationRequestRepository
                .findFirstByApprovalRequestIdOrderByIdDesc(requestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "PreAuthorizationRequest not found for approvalRequestId/requestId " + requestId,
                        "preAuthorization",
                        "notfound"
                ));
    }

    private PreAuthorizationSearchResponse searchFromWaseel(Long requestId) {
        if (mockService.isEnabled()) {
            return mockService.search(requestId);
        }

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

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String body = response.getBody();
            log.info("========== WASEEL PRE-AUTH SEARCH RESPONSE ==========");
            log.info("Status Code: {}", response.getStatusCode());
            log.info("Response Body: {}", body);
            log.info("=====================================================");

            PreAuthorizationSearchResponse parsed = parseSearchResponse(body);
            int itemCount = parsed != null && parsed.item() != null ? parsed.item().size() : 0;
            long mappedIds = parsed != null && parsed.item() != null
                    ? parsed.item().stream().filter(i -> i != null && i.itemId() != null).count()
                    : 0;
            log.info(
                    "[PREAUTH_SEARCH] Parsed search items. requestId={} itemCount={} itemIdsPresent={}",
                    requestId,
                    itemCount,
                    mappedIds
            );
            return parsed;

        } catch (HttpStatusCodeException ex) {
            logWaseelError("PRE-AUTH SEARCH", ex, null);
            throw ex;
        }
    }

    private PreAuthorizationSearchResponse parseSearchResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            PreAuthorizationSearchResponse parsed =
                    objectMapper.readValue(body, PreAuthorizationSearchResponse.class);

            if (parsed != null && parsed.item() != null && !parsed.item().isEmpty()) {
                return parsed;
            }

            // Fallback when Waseel uses an unexpected items key / shape.
            var root = objectMapper.readTree(body);
            var itemsNode = root.get("item");
            if (itemsNode == null || !itemsNode.isArray()) {
                itemsNode = root.get("items");
            }
            if (itemsNode == null || !itemsNode.isArray()) {
                itemsNode = root.get("approvalItems");
            }
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
                return parsed;
            }

            List<PreAuthorizationSearchItem> items = objectMapper.convertValue(
                    itemsNode,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class,
                            PreAuthorizationSearchItem.class
                    )
            );

            return new PreAuthorizationSearchResponse(
                    parsed != null ? parsed.approvalRequestId() : asLong(root.get("approvalRequestId")),
                    parsed != null ? parsed.approvalResponseId() : asLong(root.get("approvalResponseId")),
                    parsed != null ? parsed.payerNphiesId() : text(root.get("payerNphiesId")),
                    parsed != null ? parsed.memberCardId() : text(root.get("memberCardId")),
                    parsed != null ? parsed.insurer() : text(root.get("insurer")),
                    parsed != null ? parsed.paymentAmount() : null,
                    parsed != null ? parsed.claimResourceId() : text(root.get("claimResourceId")),
                    parsed != null ? parsed.outcome() : text(root.get("outcome")),
                    parsed != null ? parsed.status() : text(root.get("status")),
                    parsed != null ? parsed.disposition() : text(root.get("disposition")),
                    parsed != null ? parsed.period() : text(root.get("period")),
                    parsed != null ? parsed.preAuthStartDate() : null,
                    parsed != null ? parsed.preAuthEndDate() : null,
                    parsed != null ? parsed.processNotes() : text(root.get("processNotes")),
                    parsed != null ? parsed.preAuthRefNo() : text(root.get("preAuthRefNo")),
                    parsed != null ? parsed.providertransactionlogId() : asLong(root.get("providertransactionlogId")),
                    parsed != null ? parsed.transactionLogDate() : null,
                    parsed != null ? parsed.cancelStatus() : text(root.get("cancelStatus")),
                    parsed != null ? parsed.cancelResponseReason() : text(root.get("cancelResponseReason")),
                    items,
                    parsed != null ? parsed.diagnosis() : null,
                    parsed != null ? parsed.careTeam() : null,
                    parsed != null ? parsed.supportingInfo() : null,
                    parsed != null ? parsed.errors() : null
            );
        } catch (JsonProcessingException ex) {
            log.error("[PREAUTH_SEARCH] Failed to parse search response JSON", ex);
            throw new BadRequestAlertException(
                    "Failed to parse Waseel search response",
                    "preAuthorization",
                    "search.parse.failed"
            );
        }
    }

    private static Long asLong(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.valueOf(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText();
        return value != null && !value.isBlank() ? value : null;
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

        if (searchResponse.cancelStatus() != null) {
            preAuth.setIsCancelled(Boolean.TRUE);
            preAuth.setStatus(
                    firstNonBlank(
                            searchResponse.cancelStatus(),
                            searchResponse.outcome(),
                            "Cancelled"
                    )
            );
        } else {
            preAuth.setStatus(
                    firstNonBlank(
                            searchResponse.status(),
                            searchResponse.outcome(),
                            preAuth.getStatus(),
                            "UNKNOWN"
                    )
            );
        }

        preAuth.setDisposition(searchResponse.disposition());

        preAuth.setCancelStatus(searchResponse.cancelStatus());
        preAuth.setCancelMessage(searchResponse.cancelResponseReason());

        if (searchResponse.paymentAmount() != null) {
            preAuth.setTotalNet(searchResponse.paymentAmount());
        }

        preAuth.setSearchResponseJson(toJsonWithoutNulls(searchResponse));

        preAuthorizationRequestRepository.save(preAuth);
    }

    /**
     * Persist Waseel claim-item mapping from Search so Communication can use
     * the internal Waseel {@code itemId} as {@code claimItemId} (never FHIR sequence).
     * Creates local item rows when Search returns items that are not yet stored.
     */
    private void updateItemsAfterSearch(
            PreAuthorizationRequest preAuth,
            PreAuthorizationSearchResponse searchResponse
    ) {
        if (searchResponse == null || searchResponse.item() == null || searchResponse.item().isEmpty()) {
            log.warn(
                    "[PREAUTH_SEARCH] Search returned no items to map. preAuthId={} approvalRequestId={}",
                    preAuth.getId(),
                    searchResponse == null ? null : searchResponse.approvalRequestId()
            );
            return;
        }

        List<PreAuthorizationItem> localItems = new ArrayList<>(
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(preAuth.getId())
        );

        for (PreAuthorizationSearchItem searchItem : searchResponse.item()) {
            if (searchItem == null || searchItem.itemId() == null) {
                continue;
            }

            PreAuthorizationItem local = findLocalItem(localItems, searchItem);
            if (local == null) {
                local = buildItemFromSearch(preAuth.getId(), searchItem);
                localItems.add(local);
                log.info(
                        "[PREAUTH_SEARCH] Created local item from Search. preAuthId={} sequence={} itemCode={} waseelItemId={}",
                        preAuth.getId(),
                        searchItem.sequence(),
                        searchItem.itemCode(),
                        searchItem.itemId()
                );
            }

            local.setWaseelItemId(searchItem.itemId());
            if (searchItem.itemCode() != null && !searchItem.itemCode().isBlank()) {
                local.setItemCode(searchItem.itemCode());
            }
            if (searchItem.itemDescription() != null) {
                local.setItemDescription(searchItem.itemDescription());
            }
            if (searchItem.type() != null && !searchItem.type().isBlank()) {
                local.setItemType(searchItem.type());
            }
            if (searchItem.decision() != null && !searchItem.decision().isBlank()) {
                local.setItemDecision(searchItem.decision());
            } else if (searchItem.status() != null && !searchItem.status().isBlank()) {
                local.setItemDecision(searchItem.status());
            } else if (searchItem.itemDecision() != null
                    && searchItem.itemDecision().status() != null
                    && !searchItem.itemDecision().status().isBlank()) {
                local.setItemDecision(searchItem.itemDecision().status());
            }
            if (searchItem.reasonCodes() != null) {
                local.setReasonCodes(searchItem.reasonCodes());
            }
            local.setRawJson(toJsonWithoutNulls(searchItem));

            log.info(
                    "[PREAUTH_SEARCH] Stored waseelClaimItemId mapping. "
                            + "preAuthId={} approvalRequestId={} approvalResponseId={} "
                            + "sequence={} itemCode={} waseelItemId={}",
                    preAuth.getId(),
                    searchResponse.approvalRequestId(),
                    searchResponse.approvalResponseId(),
                    local.getSequence(),
                    local.getItemCode(),
                    local.getWaseelItemId()
            );
        }

        preAuthorizationItemRepository.saveAll(localItems);
    }

    private PreAuthorizationItem buildItemFromSearch(
            Long preAuthorizationId,
            PreAuthorizationSearchItem searchItem
    ) {
        Integer sequence = searchItem.sequence() != null ? searchItem.sequence() : 1;
        String itemCode = searchItem.itemCode() != null && !searchItem.itemCode().isBlank()
                ? searchItem.itemCode()
                : "UNKNOWN";
        String itemType = searchItem.type() != null && !searchItem.type().isBlank()
                ? searchItem.type()
                : "unknown";

        return PreAuthorizationItem.builder()
                .preAuthorizationId(preAuthorizationId)
                .sequence(sequence)
                .itemType(itemType)
                .itemCode(itemCode)
                .itemDescription(searchItem.itemDescription())
                .nonStandardCode(searchItem.nonStandardCode())
                .nonStandardDesc(searchItem.nonStandardDesc())
                .isPackage(Boolean.TRUE.equals(searchItem.isPackage()))
                .isMaternity(Boolean.TRUE.equals(searchItem.isMaternity()))
                .quantity(searchItem.quantity() == null ? BigDecimal.ONE : searchItem.quantity())
                .quantityCode(searchItem.quantityCode())
                .unitPrice(searchItem.unitPrice() == null ? BigDecimal.ZERO : searchItem.unitPrice())
                .discount(searchItem.discount() == null ? BigDecimal.ZERO : searchItem.discount())
                .factor(searchItem.factor() == null ? BigDecimal.ONE : searchItem.factor())
                .taxPercent(searchItem.taxPercent())
                .tax(searchItem.tax())
                .patientSharePercent(searchItem.patientSharePercent())
                .patientShare(searchItem.patientShare())
                .payerShare(searchItem.payerShare())
                .net(searchItem.net() == null ? BigDecimal.ZERO : searchItem.net())
                .waseelItemId(searchItem.itemId())
                .itemDecision(
                        firstNonBlank(
                                searchItem.decision(),
                                searchItem.status(),
                                searchItem.itemDecision() == null
                                        ? null
                                        : searchItem.itemDecision().status()
                        )
                )
                .reasonCodes(searchItem.reasonCodes())
                .rawJson(toJsonWithoutNulls(searchItem))
                .build();
    }

    private PreAuthorizationItem findLocalItem(
            List<PreAuthorizationItem> localItems,
            PreAuthorizationSearchItem searchItem
    ) {
        if (localItems == null || localItems.isEmpty() || searchItem == null) {
            return null;
        }

        if (searchItem.sequence() != null) {
            for (PreAuthorizationItem local : localItems) {
                if (Objects.equals(local.getSequence(), searchItem.sequence())) {
                    return local;
                }
            }
        }

        if (searchItem.itemCode() != null && !searchItem.itemCode().isBlank()) {
            for (PreAuthorizationItem local : localItems) {
                if (local.getItemCode() != null
                        && local.getItemCode().equalsIgnoreCase(searchItem.itemCode())) {
                    return local;
                }
            }
        }

        return null;
    }

    private Map<String, Object> snapshotPreAuthorization(PreAuthorizationRequest preAuth) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", preAuth.getId());
        snapshot.put("status", preAuth.getStatus());
        snapshot.put("outcome", preAuth.getOutcome());
        snapshot.put("disposition", preAuth.getDisposition());
        snapshot.put("approvalRequestId", preAuth.getApprovalRequestId());
        snapshot.put("approvalResponseId", preAuth.getApprovalResponseId());
        snapshot.put("preAuthRefNo", preAuth.getPreAuthRefNo());
        snapshot.put("isCancelled", preAuth.getIsCancelled());
        snapshot.put("cancelStatus", preAuth.getCancelStatus());
        snapshot.put("totalNet", preAuth.getTotalNet());

        List<Map<String, Object>> items = new ArrayList<>();
        for (PreAuthorizationItem item :
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(preAuth.getId())) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("id", item.getId());
            itemMap.put("sequence", item.getSequence());
            itemMap.put("itemCode", item.getItemCode());
            itemMap.put("waseelItemId", item.getWaseelItemId());
            itemMap.put("itemDecision", item.getItemDecision());
            items.add(itemMap);
        }
        snapshot.put("items", items);
        return snapshot;
    }

    private void saveSearchTrack(
            PreAuthorizationRequest preAuth,
            Long requestId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            PreAuthorizationSearchResponse searchResponse
    ) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("requestId", requestId);
        requestPayload.put("before", beforeState);
        requestPayload.put("after", afterState);

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
                .requestJson(toJsonWithoutNulls(requestPayload))
                .responseJson(toJsonWithoutNulls(searchResponse))
                .build();

        preAuthorizationTrackRepository.save(track);
    }

    @Transactional
    public PreAuthorizationCommunicationResponse communicate(
            PreAuthorizationCommunicationRequest request
    ) {
        ResolvedCommunication resolved = resolveCommunicationClaimItemIds(request);
        PreAuthorizationCommunicationRequest resolvedRequest = resolved.request();

        // Strip internal attachmentId before sending to Waseel.
        PreAuthorizationCommunicationRequest waseelBody = stripInternalAttachmentIds(resolvedRequest);

        if (mockService.isEnabled()) {
            PreAuthorizationCommunicationResponse mockResponse =
                    mockService.communicate(waseelBody);
            for (Long attachmentId : resolved.attachmentIds()) {
                preAuthorizationAttachmentService.markSentToWaseel(attachmentId, null);
            }
            saveCommunicationTrack(resolved.preAuth(), resolvedRequest, mockResponse, null);
            return mockResponse;
        }

        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/nphies-handle-poll-response-approval-management/providers/"
                + properties.providerId()
                + "/approval/communication";

        String jsonBody = toJsonWithoutNulls(waseelBody);

        HttpEntity<String> entity = new HttpEntity<>(
                jsonBody,
                buildHeaders(token)
        );

        try {
            log.info("========== WASEEL PRE-AUTH COMMUNICATION REQUEST ==========");
            log.info("URL: {}", url);
            log.info("Request Body: {}", toJsonWithoutNulls(sanitizeCommunicationForLog(waseelBody)));
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

            for (Long attachmentId : resolved.attachmentIds()) {
                preAuthorizationAttachmentService.markSentToWaseel(attachmentId, null);
            }

            saveCommunicationTrack(
                    resolved.preAuth(),
                    resolvedRequest,
                    response.getBody(),
                    null
            );

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            logWaseelError(
                    "PRE-AUTH COMMUNICATION",
                    ex,
                    toJsonWithoutNulls(sanitizeCommunicationForLog(waseelBody))
            );
            saveCommunicationTrack(
                    resolved.preAuth(),
                    resolvedRequest,
                    null,
                    ex.getResponseBodyAsString()
            );
            throw ex;
        }
    }

    private void saveCommunicationTrack(
            PreAuthorizationRequest preAuth,
            PreAuthorizationCommunicationRequest resolvedRequest,
            PreAuthorizationCommunicationResponse response,
            String errorBody
    ) {
        if (preAuth == null) {
            return;
        }

        // Persist history without large base64 payloads.
        PreAuthorizationCommunicationRequest historyRequest =
                sanitizeCommunicationForLog(resolvedRequest);

        PreAuthorizationTrack track = PreAuthorizationTrack.builder()
                .preAuthorization(preAuth)
                .trackType(PreAuthorizationCommunicationHistoryService.TRACK_TYPE_COMMUNICATION)
                .status(response != null ? response.status() : "ERROR")
                .outcome(response != null ? response.outcome() : "Error")
                .message(
                        response != null
                                ? firstNonBlank(response.message(), response.disposition())
                                : blankToNull(errorBody)
                )
                .disposition(response != null ? response.disposition() : null)
                .transactionId(response != null ? response.transactionId() : null)
                .outgoingTransactionId(
                        response != null && response.outgoingTransactionId() != null
                                ? String.valueOf(response.outgoingTransactionId())
                                : null
                )
                .approvalRequestId(preAuth.getApprovalRequestId())
                .approvalResponseId(
                        resolvedRequest != null
                                ? resolvedRequest.claimResponseId()
                                : preAuth.getApprovalResponseId()
                )
                .requestJson(toJsonWithoutNulls(historyRequest))
                .responseJson(
                        response != null
                                ? toJsonWithoutNulls(response)
                                : blankToNull(errorBody)
                )
                .build();

        preAuthorizationTrackRepository.save(track);
    }

    /**
     * Replace payload claimItemId with stored Waseel waseelItemId from Search.
     * Do not send item sequence as claimItemId.
     */
    private ResolvedCommunication resolveCommunicationClaimItemIds(
            PreAuthorizationCommunicationRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Communication request is required",
                    "preAuthorization",
                    "communication.required"
            );
        }

        if (request.payloads() == null || request.payloads().isEmpty()) {
            throw new BadRequestAlertException(
                    "Communication payloads are required",
                    "preAuthorization",
                    "communication.payloads.required"
            );
        }

        PreAuthorizationRequest preAuth = resolvePreAuthorizationForCommunication(request);
        List<PreAuthorizationItem> mappedItems = loadMappedClaimItems(preAuth.getId());

        /*
         * Preferred path: reuse stored Waseel itemId from a prior Search.
         * Fallback: if missing, Search once with approvalRequestId, store mapping, then continue.
         */
        if (mappedItems.isEmpty() && preAuth.getApprovalRequestId() != null) {
            log.info(
                    "[PREAUTH_COMM] No stored waseelClaimItemId — running Search to load mapping. "
                            + "preAuthId={} approvalRequestId={}",
                    preAuth.getId(),
                    preAuth.getApprovalRequestId()
            );
            PreAuthorizationSearchResponse searchResponse =
                    searchFromWaseel(preAuth.getApprovalRequestId());
            updatePreAuthorizationAfterSearch(preAuth, searchResponse);
            updateItemsAfterSearch(preAuth, searchResponse);
            mappedItems = loadMappedClaimItems(preAuth.getId());
        }

        if (mappedItems.isEmpty()) {
            throw new BadRequestAlertException(
                    "No waseelClaimItemId mapping found. Run Search first to refresh pre-authorization items.",
                    "preAuthorization",
                    "communication.claimItemId.missing"
            );
        }

        Long claimResponseId = request.claimResponseId() != null
                ? request.claimResponseId()
                : preAuth.getApprovalResponseId();

        if (claimResponseId == null) {
            throw new BadRequestAlertException(
                    "approvalResponseId/claimResponseId is required. Run Search first.",
                    "preAuthorization",
                    "communication.claimResponseId.required"
            );
        }

        List<PreAuthorizationCommunicationRequest.Payload> resolvedPayloads = new ArrayList<>();
        List<Long> attachmentIds = new ArrayList<>();
        for (PreAuthorizationCommunicationRequest.Payload payload : request.payloads()) {
            Long waseelClaimItemId = resolveWaseelClaimItemId(payload.claimItemId(), mappedItems);
            NormalizedPayload normalized =
                    normalizeCommunicationPayload(preAuth.getId(), payload, waseelClaimItemId);
            resolvedPayloads.add(normalized.payload());
            if (normalized.attachmentId() != null) {
                attachmentIds.add(normalized.attachmentId());
            }
        }

        // Waseel body: claimResponseId = approvalResponseId, claimItemId = stored Waseel itemId.
        return new ResolvedCommunication(
                preAuth,
                new PreAuthorizationCommunicationRequest(
                        null,
                        claimResponseId,
                        resolvedPayloads
                ),
                attachmentIds
        );
    }

    /**
     * Normalize text and/or attachment payload for Waseel Communication API.
     * Prefers a previously uploaded {@code attachmentId}; otherwise persists base64
     * into {@code pre_authorization_attachments} (same pattern as patient attachments).
     */
    private NormalizedPayload normalizeCommunicationPayload(
            Long preAuthorizationId,
            PreAuthorizationCommunicationRequest.Payload payload,
            Long waseelClaimItemId
    ) {
        String payloadValue = blankToNull(payload.payloadValue());
        Long attachmentId = payload.attachmentId();
        String attachmentName = blankToNull(payload.attachmentName());
        String attachmentType = blankToNull(payload.attachmentType());
        String payloadAttachment = normalizeBase64Attachment(payload.payloadAttachment());
        String createdDate = blankToNull(payload.createdDate());

        if (attachmentId != null) {
            PreAuthorizationAttachment stored =
                    preAuthorizationAttachmentService.requireOwnedAttachment(
                            attachmentId,
                            preAuthorizationId
                    );
            byte[] bytes = preAuthorizationAttachmentService.loadBytes(stored);
            attachmentName = stored.getFilename();
            attachmentType = stored.getMimeType();
            payloadAttachment = Base64.getEncoder().encodeToString(bytes);
            if (createdDate == null) {
                createdDate = java.time.LocalDate.now().toString();
            }
        } else if (payloadAttachment != null) {
            PreAuthorizationAttachment saved = preAuthorizationAttachmentService.saveFromBase64(
                    preAuthorizationId,
                    attachmentName != null ? attachmentName : "communication-attachment",
                    attachmentType != null ? attachmentType : "application/octet-stream",
                    payloadAttachment,
                    payloadValue,
                    PreAuthorizationAttachmentService.SOURCE_COMMUNICATION,
                    waseelClaimItemId
            );
            attachmentId = saved.getId();
            attachmentName = saved.getFilename();
            attachmentType = saved.getMimeType();
            if (createdDate == null) {
                createdDate = java.time.LocalDate.now().toString();
            }
        }

        boolean hasText = payloadValue != null;
        boolean hasAttachment = payloadAttachment != null;

        if (!hasText && !hasAttachment) {
            throw new BadRequestAlertException(
                    "Communication payload requires payloadValue and/or attachment",
                    "preAuthorization",
                    "communication.payload.content.required"
            );
        }

        if (hasAttachment) {
            if (attachmentName == null) {
                throw new BadRequestAlertException(
                        "attachmentName is required when sending an attachment",
                        "preAuthorization",
                        "communication.attachmentName.required"
                );
            }
            if (attachmentType == null) {
                throw new BadRequestAlertException(
                        "attachmentType is required when sending an attachment",
                        "preAuthorization",
                        "communication.attachmentType.required"
                );
            }
            if (createdDate == null) {
                createdDate = java.time.LocalDate.now().toString();
            }
        } else {
            attachmentName = null;
            attachmentType = null;
            payloadAttachment = null;
            attachmentId = null;
        }

        return new NormalizedPayload(
                new PreAuthorizationCommunicationRequest.Payload(
                        attachmentName,
                        attachmentType,
                        waseelClaimItemId,
                        createdDate,
                        payloadAttachment,
                        payloadValue,
                        attachmentId
                ),
                attachmentId
        );
    }

    private PreAuthorizationCommunicationRequest stripInternalAttachmentIds(
            PreAuthorizationCommunicationRequest request
    ) {
        if (request == null || request.payloads() == null) {
            return request;
        }

        List<PreAuthorizationCommunicationRequest.Payload> payloads = request.payloads().stream()
                .map(payload -> new PreAuthorizationCommunicationRequest.Payload(
                        payload.attachmentName(),
                        payload.attachmentType(),
                        payload.claimItemId(),
                        payload.createdDate(),
                        payload.payloadAttachment(),
                        payload.payloadValue(),
                        null
                ))
                .toList();

        return new PreAuthorizationCommunicationRequest(
                request.preAuthorizationId(),
                request.claimResponseId(),
                payloads
        );
    }

    private record ResolvedCommunication(
            PreAuthorizationRequest preAuth,
            PreAuthorizationCommunicationRequest request,
            List<Long> attachmentIds
    ) {}

    private record NormalizedPayload(
            PreAuthorizationCommunicationRequest.Payload payload,
            Long attachmentId
    ) {}

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Accept raw base64 or data-URL ({@code data:mime;base64,...}) and return raw base64 only.
     */
    private static String normalizeBase64Attachment(String value) {
        String attachment = blankToNull(value);
        if (attachment == null) {
            return null;
        }

        int commaIndex = attachment.indexOf(',');
        if (attachment.startsWith("data:") && commaIndex > 0) {
            attachment = attachment.substring(commaIndex + 1).trim();
        }

        return attachment.isEmpty() ? null : attachment;
    }

    private PreAuthorizationCommunicationRequest sanitizeCommunicationForLog(
            PreAuthorizationCommunicationRequest request
    ) {
        if (request == null || request.payloads() == null) {
            return request;
        }

        List<PreAuthorizationCommunicationRequest.Payload> sanitized = request.payloads().stream()
                .map(payload -> {
                    String attachment = payload.payloadAttachment();
                    String truncated = attachment == null
                            ? null
                            : "[base64 length=" + attachment.length() + "]";
                    return new PreAuthorizationCommunicationRequest.Payload(
                            payload.attachmentName(),
                            payload.attachmentType(),
                            payload.claimItemId(),
                            payload.createdDate(),
                            truncated,
                            payload.payloadValue(),
                            payload.attachmentId()
                    );
                })
                .toList();

        return new PreAuthorizationCommunicationRequest(
                request.preAuthorizationId(),
                request.claimResponseId(),
                sanitized
        );
    }

    private List<PreAuthorizationItem> loadMappedClaimItems(Long preAuthorizationId) {
        return preAuthorizationItemRepository
                .findByPreAuthorizationIdOrderBySequenceAsc(preAuthorizationId)
                .stream()
                .filter(item -> item.getWaseelItemId() != null)
                .toList();
    }

    private PreAuthorizationRequest resolvePreAuthorizationForCommunication(
            PreAuthorizationCommunicationRequest request
    ) {
        if (request.preAuthorizationId() != null) {
            return preAuthorizationRequestRepository
                    .findById(request.preAuthorizationId())
                    .orElseThrow(() -> new BadRequestAlertException(
                            "PreAuthorizationRequest not found with id " + request.preAuthorizationId(),
                            "preAuthorization",
                            "notfound"
                    ));
        }

        if (request.claimResponseId() != null) {
            return preAuthorizationRequestRepository
                    .findFirstByApprovalResponseIdOrderByIdDesc(request.claimResponseId())
                    .orElseThrow(() -> new BadRequestAlertException(
                            "PreAuthorizationRequest not found for claimResponseId "
                                    + request.claimResponseId()
                                    + ". Run Search first.",
                            "preAuthorization",
                            "notfound"
                    ));
        }

        throw new BadRequestAlertException(
                "preAuthorizationId or claimResponseId is required",
                "preAuthorization",
                "communication.preAuthorization.required"
        );
    }

    private Long resolveWaseelClaimItemId(
            Long requestedClaimItemId,
            List<PreAuthorizationItem> mappedItems
    ) {
        // Already a stored Waseel item id.
        if (requestedClaimItemId != null) {
            for (PreAuthorizationItem item : mappedItems) {
                if (Objects.equals(item.getWaseelItemId(), requestedClaimItemId)) {
                    return item.getWaseelItemId();
                }
            }

            // Client sent sequence by mistake — map sequence -> waseelItemId.
            for (PreAuthorizationItem item : mappedItems) {
                if (Objects.equals(item.getSequence(), requestedClaimItemId.intValue())) {
                    log.info(
                            "[PREAUTH_COMM] Replaced sequence {} with waseelClaimItemId {}",
                            requestedClaimItemId,
                            item.getWaseelItemId()
                    );
                    return item.getWaseelItemId();
                }
            }
        }

        // Frontend may omit claimItemId; use first stored Waseel item id.
        if (!mappedItems.isEmpty()) {
            return mappedItems.get(0).getWaseelItemId();
        }

        throw new BadRequestAlertException(
                "Unable to resolve waseelClaimItemId for communication. "
                        + "Pass a stored waseel item id, or run Search first.",
                "preAuthorization",
                "communication.claimItemId.unresolved"
        );
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
        if (mockService.isEnabled()) {
            return mockService.cancel(request);
        }

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
                            searchResponse.cancelStatus(),
                            searchResponse.outcome(),
                            cancelResponse != null ? cancelResponse.outcome() : null,
                            cancelResponse != null ? cancelResponse.status() : null,
                            "Cancelled"
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