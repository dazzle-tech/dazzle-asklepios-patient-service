package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.ClaimItem;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingCriteria;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingItemResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimValidationError;
import com.dazzle.asklepios.repository.ClaimItemRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.ClaimTrackingSpecification;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimTrackingService {

    private final ClaimRequestRepository claimRequestRepository;
    private final ClaimItemRepository claimItemRepository;
    private final ObjectMapper objectMapper;

    public Page<ClaimTrackingResponse> findAll(Pageable pageable) {
        return findAll(null, pageable);
    }

    public Page<ClaimTrackingResponse> findAll(ClaimTrackingCriteria criteria, Pageable pageable) {
        Page<ClaimRequest> claims = claimRequestRepository.findAll(ClaimTrackingSpecification.of(criteria), pageable);
        Map<Long, List<ClaimItem>> itemsByClaim = itemsByClaim(claims.getContent());
        return claims.map(claim -> toResponse(claim, itemsByClaim.getOrDefault(claim.getId(), List.of())));
    }

    /**
     * Loads the items of a whole page in one query instead of one query per claim.
     */
    private Map<Long, List<ClaimItem>> itemsByClaim(List<ClaimRequest> claims) {
        List<Long> claimIds = claims.stream().map(ClaimRequest::getId).toList();
        if (claimIds.isEmpty()) {
            return Map.of();
        }
        return claimItemRepository.findByClaimRequestIdIn(claimIds).stream()
                .sorted(Comparator.comparing(
                        ClaimItem::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .collect(Collectors.groupingBy(ClaimItem::getClaimRequestId));
    }

    public ClaimTrackingResponse findById(Long id) {
        ClaimRequest entity = claimRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Claim not found with id " + id,
                        "claim",
                        "notfound"
                ));
        return toResponse(entity);
    }

    public Optional<ClaimTrackingResponse> findOptionalByFinancialDocumentId(Long financialDocumentId) {
        return claimRequestRepository
                .findFirstByFinancialDocumentIdOrderByIdDesc(financialDocumentId)
                .map(this::toResponse);
    }

    public ClaimTrackingResponse findByFinancialDocumentId(Long financialDocumentId) {
        return findOptionalByFinancialDocumentId(financialDocumentId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Claim not found for invoice " + financialDocumentId,
                        "claim",
                        "notfound"
                ));
    }

    private ClaimTrackingResponse toResponse(ClaimRequest e) {
        return toResponse(e, claimItemRepository.findByClaimRequestIdOrderBySequenceAsc(e.getId()));
    }

    private ClaimTrackingResponse toResponse(ClaimRequest e, List<ClaimItem> items) {
        List<ClaimTrackingItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        boolean canResubmit = e.getStatus() == ClaimStatus.FAILED
                || e.getStatus() == ClaimStatus.DRAFT
                || e.getStatus() == ClaimStatus.REJECTED;

        boolean canRefreshUpload = e.getUploadId() != null
                && (e.getStatus() == ClaimStatus.SUBMITTED
                || e.getStatus() == ClaimStatus.ACCEPTED
                || e.getStatus() == ClaimStatus.REJECTED);

        return new ClaimTrackingResponse(
                e.getId(),
                e.getPatientId(),
                e.getEncounterId(),
                e.getFinancialDocumentId(),
                e.getPreAuthorizationId(),
                e.getPatientInsuranceId(),
                e.getClaimType(),
                e.getClaimSubType(),
                e.getUploadName(),
                e.getUploadId(),
                e.getProvClaimNo(),
                e.getClaimReference(),
                e.getSettlementNo(),
                e.getPreAuthRefNo(),
                e.getApprovalResponseId(),
                e.getTotalNet(),
                e.getApprovedAmount(),
                e.getWaseelStatus(),
                e.getStatus(),
                e.getOutcome(),
                e.getMessage(),
                e.getSubmittedAt(),
                e.getCreatedDate(),
                e.getCreatedBy(),
                e.getLastModifiedDate(),
                e.getLastModifiedBy(),
                canResubmit,
                canRefreshUpload,
                itemResponses,
                readValidationErrors(e.getValidationErrorsJson()),
                resolveStatusDescription(e)
        );
    }

    private List<ClaimValidationError> readValidationErrors(String validationErrorsJson) {
        if (validationErrorsJson == null || validationErrorsJson.isBlank()) {
            return List.of();
        }
        try {
            List<ClaimValidationError> parsed = objectMapper.readValue(
                    validationErrorsJson,
                    new TypeReference<>() {}
            );
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String resolveStatusDescription(ClaimRequest claim) {
        if (claim.getStatus() == ClaimStatus.REJECTED || "NOT_ACCEPTED".equalsIgnoreCase(claim.getOutcome())) {
            List<ClaimValidationError> errors = readValidationErrors(claim.getValidationErrorsJson());
            if (!errors.isEmpty()) {
                return errors.get(0).message();
            }
            return claim.getMessage() == null || claim.getMessage().isBlank()
                    ? "Claim Is Not Saved Successfully."
                    : claim.getMessage();
        }
        return claim.getMessage();
    }

    private ClaimTrackingItemResponse toItemResponse(ClaimItem item) {
        return new ClaimTrackingItemResponse(
                item.getId(),
                item.getSequence(),
                item.getPatientServiceProductId(),
                item.getFinancialDocumentItemId(),
                item.getBillingChargeLineId(),
                item.getItemType(),
                item.getItemCode(),
                item.getItemDescription(),
                item.getInvoiceNo(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getNet(),
                item.getPatientShare(),
                item.getPayerShare()
        );
    }
}
