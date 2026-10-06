package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimValidationError;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.service.ClaimSettlementNumberService;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimStatusRefreshService {

    private final ClaimRequestRepository claimRequestRepository;
    private final ClaimSettlementNumberService claimSettlementNumberService;
    private final WaseelClaimService waseelClaimService;
    private final ClaimPayloadValidationService claimPayloadValidationService;
    private final ObjectMapper objectMapper;
    private final Map<Long, TimedLookup> recentLookups = new ConcurrentHashMap<>();

    @Transactional
    public ClaimRequest refresh(Long claimId) {
        return refresh(claimId, true);
    }

    @Transactional
    public ClaimRequest refresh(Long claimId, boolean assignSettlement) {
        ClaimRequest claim = claimRequestRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Claim not found with id " + claimId,
                        "claim",
                        "notfound"
                ));

        List<ClaimValidationError> errors = new ArrayList<>();
        ClaimStatus statusBeforeSummary = claim.getStatus();

        if (claim.getUploadId() != null) {
            try {
                WaseelClaimUploadResponse summary =
                        waseelClaimService.getUploadSummary(claim.getUploadId());
                applyUploadSummary(claim, summary);

                applySummaryStatus(claim, summary);
            } catch (RestClientException ex) {
                log.warn("[CLAIM_REFRESH] Upload summary failed claimId={} uploadId={}",
                        claimId, claim.getUploadId(), ex);
            }
        }

        JsonNode claimSearch = null;
        if (claim.getProvClaimNo() != null && !claim.getProvClaimNo().isBlank()) {
            try {
                String searchBody = waseelClaimService.searchClaimByProvClaimNo(claim.getProvClaimNo());
                errors.addAll(parseWaseelErrors(searchBody));
                claimSearch = readTree(searchBody);
                claim.setResponseJson(mergeResponseJson(claim.getResponseJson(), searchBody));
            } catch (HttpStatusCodeException ex) {
                log.warn(
                        "[CLAIM_REFRESH] Claim search failed claimId={} provClaimNo={} status={}",
                        claimId,
                        claim.getProvClaimNo(),
                        ex.getStatusCode().value()
                );
            } catch (RestClientException ex) {
                log.warn("[CLAIM_REFRESH] Claim search failed claimId={} provClaimNo={}",
                        claimId, claim.getProvClaimNo(), ex);
            }
        }

        errors.addAll(readStoredValidationErrors(claim.getValidationErrorsJson()));

        Map<String, List<ClaimValidationError>> notAcceptedClaims = null;
        if ("PARTIAL".equals(claim.getOutcome()) && claim.getUploadId() != null) {
            notAcceptedClaims = loadNotAcceptedClaims(claim.getUploadId(), claimId);
        }

        if (errors.isEmpty()
                && !"PARTIAL".equals(claim.getOutcome())
                && claim.getRequestJson() != null
                && !claim.getRequestJson().isBlank()) {
            errors.addAll(revalidateRequestJson(claim.getRequestJson()));
        }

        List<ClaimValidationError> uploadErrors = uploadErrorsFor(notAcceptedClaims, claim.getProvClaimNo());
        if (!uploadErrors.isEmpty()) {
            errors.addAll(uploadErrors);
        }

        claim.setValidationErrorsJson(toJson(dedupeErrors(errors)));
        applyIndividualStatus(claim, claimSearch, errors, statusBeforeSummary, notAcceptedClaims);
        if (assignSettlement) {
            claimSettlementNumberService.syncSettlementNo(claim);
        } else if (claim.getStatus() != ClaimStatus.ACCEPTED) {
            claim.setSettlementNo(null);
        }

        return claimRequestRepository.save(claim);
    }

    /**
     * Upload summary counts describe the whole file. A mixed file must not mark every claim rejected.
     * All-accepted and all-rejected files keep a single status.
     */
    public void applySummaryStatus(ClaimRequest claim, WaseelClaimUploadResponse summary) {
        if (claim == null || summary == null) {
            return;
        }

        int accepted = count(summary.noOfAcceptedClaims());
        int rejected = count(summary.noOfNotAcceptedClaims());

        if (rejected > 0 && accepted == 0) {
            claim.setStatus(ClaimStatus.REJECTED);
            claim.setOutcome("NOT_ACCEPTED");
            if (claim.getMessage() == null || claim.getMessage().isBlank()) {
                claim.setMessage(
                        "Waseel rejected "
                                + rejected
                                + " claim(s)"
                                + (claim.getUploadId() == null
                                        ? "."
                                        : " in upload " + claim.getUploadId() + ".")
                );
            }
            return;
        }

        if (accepted > 0 && rejected == 0) {
            claim.setStatus(ClaimStatus.ACCEPTED);
            claim.setOutcome("ACCEPTED");
            return;
        }

        if (accepted > 0 && rejected > 0) {
            claim.setStatus(ClaimStatus.SUBMITTED);
            claim.setOutcome("PARTIAL");
            claim.setMessage(
                    "Waseel accepted " + accepted + " and rejected " + rejected + " claim(s)."
            );
        }
    }

    private void applyIndividualStatus(
            ClaimRequest claim,
            JsonNode claimSearch,
            List<ClaimValidationError> errors,
            ClaimStatus statusBeforeSummary,
            Map<String, List<ClaimValidationError>> notAcceptedClaims
    ) {
        if (claim.getStatus() == ClaimStatus.FAILED) {
            return;
        }

        if ("PARTIAL".equals(claim.getOutcome()) && notAcceptedClaims != null && !notAcceptedClaims.isEmpty()) {
            List<ClaimValidationError> uploadErrors = uploadErrorsFor(notAcceptedClaims, claim.getProvClaimNo());
            if (!uploadErrors.isEmpty()) {
                claim.setStatus(ClaimStatus.REJECTED);
                claim.setOutcome("NOT_ACCEPTED");
                claim.setMessage(uploadErrors.get(0).message());
                return;
            }
            claim.setStatus(ClaimStatus.ACCEPTED);
            claim.setOutcome("ACCEPTED");
            claim.setMessage(null);
            claim.setValidationErrorsJson(toJson(List.of()));
            return;
        }

        if (!"PARTIAL".equals(claim.getOutcome())) {
            if (!errors.isEmpty()) {
                claim.setStatus(ClaimStatus.REJECTED);
                if (claim.getOutcome() == null || claim.getOutcome().isBlank()) {
                    claim.setOutcome("NOT_ACCEPTED");
                }
            }
            return;
        }

        ClaimStatus individual = classifyIndividualClaim(claimSearch, claim.getProvClaimNo());
        if (individual == ClaimStatus.ACCEPTED) {
            claim.setStatus(ClaimStatus.ACCEPTED);
            claim.setOutcome("ACCEPTED");
            claim.setMessage(null);
            claim.setValidationErrorsJson(toJson(List.of()));
            return;
        }

        if (individual == ClaimStatus.REJECTED || !errors.isEmpty()) {
            claim.setStatus(ClaimStatus.REJECTED);
            claim.setOutcome("NOT_ACCEPTED");
            if (errors.isEmpty()) {
                claim.setMessage("Waseel did not accept this claim.");
            }
            return;
        }

        if (statusBeforeSummary == ClaimStatus.ACCEPTED || statusBeforeSummary == ClaimStatus.REJECTED) {
            claim.setStatus(statusBeforeSummary);
            claim.setOutcome(statusBeforeSummary == ClaimStatus.ACCEPTED ? "ACCEPTED" : "NOT_ACCEPTED");
        }
    }

    private Map<String, List<ClaimValidationError>> loadNotAcceptedClaims(Long uploadId, Long claimId) {
        TimedLookup cached = recentLookups.get(uploadId);
        if (cached != null && System.currentTimeMillis() - cached.atMillis() < 20_000L) {
            return cached.value();
        }
        Map<String, List<ClaimValidationError>> latest = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                latest = waseelClaimService.findNotAcceptedClaims(uploadId);
                if (latest != null && !latest.isEmpty()) {
                    recentLookups.put(uploadId, new TimedLookup(System.currentTimeMillis(), latest));
                    return latest;
                }
            } catch (RestClientException ex) {
                log.warn(
                        "[CLAIM_REFRESH] Upload claim details failed claimId={} uploadId={} attempt={}",
                        claimId,
                        uploadId,
                        attempt,
                        ex
                );
            }
            if (attempt < 4) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return latest;
                }
            }
        }
        recentLookups.put(uploadId, new TimedLookup(System.currentTimeMillis(), latest));
        return latest;
    }

    private record TimedLookup(long atMillis, Map<String, List<ClaimValidationError>> value) {
    }

    private List<ClaimValidationError> uploadErrorsFor(
            Map<String, List<ClaimValidationError>> notAcceptedClaims,
            String provClaimNo
    ) {
        if (notAcceptedClaims == null || provClaimNo == null || provClaimNo.isBlank()) {
            return List.of();
        }
        List<ClaimValidationError> matched = notAcceptedClaims.get(provClaimNo.trim().toUpperCase(Locale.ROOT));
        return matched == null ? List.of() : matched;
    }

    private ClaimStatus classifyIndividualClaim(JsonNode searchBody, String provClaimNo) {
        JsonNode claimNode = claimNode(searchBody, provClaimNo);
        if (claimNode == null) {
            return null;
        }

        String status = firstNonBlank(
                text(claimNode.get("status")),
                text(claimNode.get("claimStatus")),
                text(claimNode.get("outcome")),
                text(claimNode.get("disposition"))
        );
        if (status == null) {
            return null;
        }

        String normalized = status.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        if (normalized.contains("notaccepted")
                || normalized.contains("notsaved")
                || normalized.contains("rejected")
                || normalized.contains("denied")) {
            return ClaimStatus.REJECTED;
        }
        if (normalized.contains("accepted")) {
            return ClaimStatus.ACCEPTED;
        }
        return null;
    }

    private JsonNode claimNode(JsonNode root, String provClaimNo) {
        if (root == null || root.isNull()) {
            return null;
        }

        if (root.isArray()) {
            JsonNode matched = null;
            for (JsonNode child : root) {
                if (matchesProvClaimNo(child, provClaimNo)) {
                    return child;
                }
                if (matched == null) {
                    matched = child;
                }
            }
            return root.size() == 1 ? matched : null;
        }

        if (!root.isObject()) {
            return null;
        }

        JsonNode claims = firstPresent(root, "claims", "content", "claimList");
        if (claims != null && claims.isArray()) {
            return claimNode(claims, provClaimNo);
        }

        JsonNode nested = firstPresent(root, "claim", "claimResponse", "data");
        if (nested != null && nested.isObject() && hasClaimIdentity(nested)) {
            return nested;
        }

        return root;
    }

    private boolean matchesProvClaimNo(JsonNode node, String provClaimNo) {
        if (node == null || provClaimNo == null || provClaimNo.isBlank()) {
            return false;
        }
        String value = firstNonBlank(
                text(node.get("provClaimNo")),
                text(node.get("provclaimno")),
                text(node.get("providerClaimNo"))
        );
        return provClaimNo.equalsIgnoreCase(value);
    }

    private boolean hasClaimIdentity(JsonNode node) {
        return text(node.get("status")) != null
                || text(node.get("claimStatus")) != null
                || text(node.get("provClaimNo")) != null
                || text(node.get("outcome")) != null;
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            log.warn("[CLAIM_REFRESH] Failed to read claim search JSON", ex);
            return null;
        }
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private void applyUploadSummary(ClaimRequest claim, WaseelClaimUploadResponse summary) {
        if (summary == null) {
            return;
        }
        claim.setUploadId(summary.uploadId() == null ? claim.getUploadId() : summary.uploadId());
        claim.setUploadName(summary.uploadName() == null ? claim.getUploadName() : summary.uploadName());
        if (summary.message() != null && !summary.message().isBlank()) {
            claim.setMessage(summary.message());
        }
        try {
            claim.setResponseJson(objectMapper.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            log.warn("[CLAIM_REFRESH] Failed to serialize upload summary claimId={}", claim.getId(), ex);
        }
    }

    private List<ClaimValidationError> revalidateRequestJson(String requestJson) {
        try {
            JsonNode uploadNode = objectMapper.readTree(requestJson);
            JsonNode claimsNode = uploadNode.get("claims");
            if (claimsNode == null || !claimsNode.isArray() || claimsNode.isEmpty()) {
                return List.of();
            }
            return claimPayloadValidationService.validate(
                    objectMapper.treeToValue(claimsNode.get(0), com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimRequest.class)
            );
        } catch (JsonProcessingException ex) {
            log.warn("[CLAIM_REFRESH] Failed to revalidate stored request JSON", ex);
            return List.of();
        }
    }

    private List<ClaimValidationError> readStoredValidationErrors(String validationErrorsJson) {
        if (validationErrorsJson == null || validationErrorsJson.isBlank()) {
            return List.of();
        }
        try {
            List<ClaimValidationError> parsed = objectMapper.readValue(
                    validationErrorsJson,
                    new TypeReference<>() {}
            );
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<ClaimValidationError> parseWaseelErrors(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        List<ClaimValidationError> errors = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            collectErrors(root, errors);

            String status = text(root.get("status"));
            String disposition = text(root.get("disposition"));
            if (errors.isEmpty() && status != null && status.toLowerCase(Locale.ROOT).contains("notaccepted")) {
                errors.add(new ClaimValidationError(
                        "Claim Status",
                        firstNonBlank(disposition, "Claim Is Not Saved Successfully."),
                        "Errors & Warnings"
                ));
            }
        } catch (JsonProcessingException ex) {
            log.warn("[CLAIM_REFRESH] Failed to parse Waseel claim search response", ex);
        }
        return dedupeErrors(errors);
    }

    private void collectErrors(JsonNode node, List<ClaimValidationError> errors) {
        if (node == null) {
            return;
        }

        if (node.isArray()) {
            node.forEach(child -> collectErrors(child, errors));
            return;
        }

        if (node.isObject()) {
            JsonNode nestedErrors = firstPresent(node, "errors", "errorList", "validationErrors", "claimErrors");
            if (nestedErrors != null) {
                if (nestedErrors.isArray()) {
                    nestedErrors.forEach(item -> addErrorNode(item, errors));
                } else {
                    addErrorNode(nestedErrors, errors);
                }
            }

            JsonNode warnings = firstPresent(node, "warnings", "warningList");
            if (warnings != null && warnings.isArray()) {
                warnings.forEach(item -> addErrorNode(item, errors));
            }

            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey().toLowerCase(Locale.ROOT);
                if (field.contains("error") || field.contains("warning")) {
                    collectErrors(entry.getValue(), errors);
                }
            });
        }
    }

    private void addErrorNode(JsonNode node, List<ClaimValidationError> errors) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isTextual()) {
            errors.add(new ClaimValidationError("Waseel", node.asText(), "Errors & Warnings"));
            return;
        }

        String code = firstNonBlank(
                text(node.get("code")),
                text(node.get("field")),
                text(node.get("errorCode")),
                text(node.get("name"))
        );
        String message = firstNonBlank(
                text(node.get("message")),
                text(node.get("description")),
                text(node.get("detail")),
                text(node.get("disposition")),
                text(node.get("value"))
        );
        String section = firstNonBlank(
                text(node.get("section")),
                text(node.get("category")),
                text(node.get("tab")),
                "Errors & Warnings"
        );

        if (message != null) {
            errors.add(new ClaimValidationError(
                    code == null ? section : code,
                    message,
                    section
            ));
        }
    }

    private JsonNode firstPresent(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                return node.get(name);
            }
        }
        return null;
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

    private List<ClaimValidationError> dedupeErrors(List<ClaimValidationError> errors) {
        Set<String> seen = new LinkedHashSet<>();
        List<ClaimValidationError> deduped = new ArrayList<>();
        for (ClaimValidationError error : errors) {
            if (error == null) {
                continue;
            }
            String key = (error.code() == null ? "" : error.code())
                    + "|"
                    + (error.message() == null ? "" : error.message());
            if (seen.add(key)) {
                deduped.add(error);
            }
        }
        return deduped;
    }

    private String mergeResponseJson(String existing, String searchBody) {
        if (searchBody == null || searchBody.isBlank()) {
            return existing;
        }
        try {
            JsonNode searchNode = objectMapper.readTree(searchBody);
            if (existing == null || existing.isBlank()) {
                return objectMapper.writeValueAsString(searchNode);
            }
            JsonNode existingNode = objectMapper.readTree(existing);
            if (existingNode.isObject()) {
                var merged = objectMapper.createObjectNode();
                merged.setAll((com.fasterxml.jackson.databind.node.ObjectNode) existingNode);
                merged.set("claimSearch", searchNode);
                return objectMapper.writeValueAsString(merged);
            }
            return objectMapper.writeValueAsString(searchNode);
        } catch (JsonProcessingException ex) {
            return searchBody;
        }
    }

    private String toJson(List<ClaimValidationError> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize claim validation errors", ex);
        }
    }
}
