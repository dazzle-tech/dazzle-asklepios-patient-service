package com.dazzle.asklepios.integration.waseel.controller;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimSubmissionResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.dazzle.asklepios.integration.waseel.service.ClaimSubmissionService;
import com.dazzle.asklepios.integration.waseel.service.ClaimTrackingService;
import com.dazzle.asklepios.integration.waseel.service.WaseelClaimService;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class WaseelClaimController {

    private final ClaimSubmissionService claimSubmissionService;
    private final ClaimTrackingService claimTrackingService;
    private final WaseelClaimService waseelClaimService;

    @GetMapping("/internal/waseel/claims/tracking")
    public Page<ClaimTrackingResponse> getAll(Pageable pageable) {
        return claimTrackingService.findAll(pageable);
    }

    @GetMapping("/internal/waseel/claims/tracking/{id}")
    public ClaimTrackingResponse getOne(@PathVariable Long id) {
        return claimTrackingService.findById(id);
    }

    @GetMapping("/internal/waseel/invoices/{financialDocumentId}/claim")
    public ClaimTrackingResponse getByInvoice(@PathVariable Long financialDocumentId) {
        return claimTrackingService.findOptionalByFinancialDocumentId(financialDocumentId).orElse(null);
    }

    @GetMapping("/internal/waseel/encounters/{encounterId}/claims")
    public List<ClaimSubmissionResponse> listByEncounter(@PathVariable Long encounterId) {
        return claimSubmissionService.listByEncounter(encounterId).stream()
                .map(this::toSubmissionResponse)
                .toList();
    }

    @PostMapping("/internal/waseel/invoices/{financialDocumentId}/claims/submit")
    public ClaimSubmissionResponse submit(@PathVariable Long financialDocumentId) {
        ClaimRequest claim = claimSubmissionService.submitForInsuranceInvoice(financialDocumentId);
        if (claim == null) {
            throw new NotFoundAlertException(
                    "No claim was generated for invoice " + financialDocumentId,
                    "claim",
                    "claim.notGenerated"
            );
        }
        return toSubmissionResponse(claim);
    }

    @GetMapping("/internal/waseel/claims/uploads/{uploadId}")
    public WaseelClaimUploadResponse uploadSummary(@PathVariable Long uploadId) {
        return waseelClaimService.getUploadSummary(uploadId);
    }

    private ClaimSubmissionResponse toSubmissionResponse(ClaimRequest claim) {
        return new ClaimSubmissionResponse(
                claim.getId(),
                claim.getEncounterId(),
                claim.getFinancialDocumentId(),
                claim.getPreAuthorizationId(),
                claim.getUploadName(),
                claim.getUploadId(),
                claim.getProvClaimNo(),
                claim.getClaimReference(),
                claim.getPreAuthRefNo(),
                claim.getTotalNet(),
                claim.getStatus(),
                claim.getOutcome(),
                claim.getMessage(),
                claim.getSubmittedAt()
        );
    }
}
