package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimValidationError;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimStatusRefreshService {

    private final ClaimRequestRepository claimRequestRepository;
    private final WaseelClaimService waseelClaimService;
    private final ClaimPayloadValidationService claimPayloadValidationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ClaimRequest refresh(Long claimId) {
        ClaimRequest claim = claimRequestRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Claim not found with id " + claimId,
                        "claim",
                        "notfound"
                ));

        List<ClaimValidationError> errors = new ArrayList<>();

        if (claim.getUploadId() != null) {
            try {
                WaseelClaimUploadResponse summary =
                        waseelClaimService.getUploadSummary(claim.getUploadId());
                applyUploadSummary(claim, summary);

                if (summary != null
                        && summary.noOfNotAcceptedClaims() != null
                        && summary.noOfNotAcceptedClaims() > 0) {
                    claim.setStatus(ClaimStatus.REJECTED);
                    claim.setOutcome("NOT_ACCEPTED");
                    if (claim.getMessage() == null || claim.getMessage().isBlank()) {
                        claim.setMessage(
                                "Waseel rejected "
                                        + summary.noOfNotAcceptedClaims()
                                        + " claim(s) in upload "
                                        + claim.getUploadId()
                        );
                    }
                } else if (summary != null
                        && summary.noOfAcceptedClaims() != null
                        && summary.noOfAcceptedClaims() > 0) {
                    claim.setStatus(ClaimStatus.ACCEPTED);
                    claim.setOutcome("ACCEPTED");
                }
            } catch (RestClientException ex) {
                log.warn("[CLAIM_REFRESH] Upload summary failed claimId={} uploadId={}",
                        claimId, claim.getUploadId(), ex);
            }
        }

        if (claim.getProvClaimNo() != null && !claim.getProvClaimNo().isBlank()) {
            try {
                String searchBody = waseelClaimService.searchClaimByProvClaimNo(claim.getProvClaimNo());
                errors.addAll(parseWaseelErrors(searchBody));
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

        if (errors.isEmpty() && claim.getRequestJson() != null && !claim.getRequestJson().isBlank()) {
            errors.addAll(revalidateRequestJson(claim.getRequestJson()));
        }

        claim.setValidationErrorsJson(toJson(dedupeErrors(errors)));

        if (!errors.isEmpty() && claim.getStatus() != ClaimStatus.FAILED) {
            claim.setStatus(ClaimStatus.REJECTED);
            if (claim.getOutcome() == null || claim.getOutcome().isBlank()) {
                claim.setOutcome("NOT_ACCEPTED");
            }
        }

        return claimRequestRepository.save(claim);
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
