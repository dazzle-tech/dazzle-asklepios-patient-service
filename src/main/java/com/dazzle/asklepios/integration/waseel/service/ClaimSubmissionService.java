package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.ClaimItem;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.dazzle.asklepios.integration.waseel.event.InsuranceInvoiceIssuedEvent;
import com.dazzle.asklepios.repository.ClaimItemRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ClaimSubmissionService {

    private static final List<ClaimStatus> ACTIVE_STATUSES = List.of(
            ClaimStatus.SUBMITTING,
            ClaimStatus.SUBMITTED,
            ClaimStatus.ACCEPTED
    );

    private final ClaimRequestBuilderService claimRequestBuilderService;
    private final WaseelClaimService waseelClaimService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final ClaimItemRepository claimItemRepository;
    private final ObjectMapper objectMapper;
    private final ClaimSubmissionService self;

    public ClaimSubmissionService(
            ClaimRequestBuilderService claimRequestBuilderService,
            WaseelClaimService waseelClaimService,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService,
            FinancialDocumentRepository financialDocumentRepository,
            ClaimRequestRepository claimRequestRepository,
            ClaimItemRepository claimItemRepository,
            ObjectMapper objectMapper,
            @Lazy ClaimSubmissionService self
    ) {
        this.claimRequestBuilderService = claimRequestBuilderService;
        this.waseelClaimService = waseelClaimService;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
        this.financialDocumentRepository = financialDocumentRepository;
        this.claimRequestRepository = claimRequestRepository;
        this.claimItemRepository = claimItemRepository;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInsuranceInvoiceIssued(InsuranceInvoiceIssuedEvent event) {
        if (event == null
                || event.encounterId() == null
                || event.financialDocumentId() == null) {
            return;
        }

        try {
            self.submitForInsuranceInvoice(event.financialDocumentId());
        } catch (Exception ex) {
            log.error(
                    "[CLAIM_SUBMIT] Failed after insurance invoice finalize. encounterId={} documentId={}",
                    event.encounterId(),
                    event.financialDocumentId(),
                    ex
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClaimRequest submitForInsuranceInvoice(Long financialDocumentId) {
        FinancialDocument invoice = financialDocumentRepository.findById(financialDocumentId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Insurance invoice not found",
                        "claim",
                        "invoice.notFound"
                ));

        if (invoice.getDocumentType() != FinancialDocumentType.INVOICE
                || invoice.getDocumentSubtype() != FinancialDocumentSubtype.INSURANCE_CLAIM) {
            throw new BadRequestAlertException(
                    "Only finalized insurance claim invoices can generate Waseel claims",
                    "claim",
                    "invoice.subtype.invalid"
            );
        }

        if (!encounterInsuranceEligibilityService.isInsuranceEncounter(invoice.getEncounterId())) {
            log.info(
                    "[CLAIM_SUBMIT] Skipping claim for non-insurance encounterId={}",
                    invoice.getEncounterId()
            );
            return null;
        }

        if (claimRequestRepository.existsByFinancialDocumentIdAndStatusIn(
                invoice.getId(),
                ACTIVE_STATUSES
        )) {
            log.info(
                    "[CLAIM_SUBMIT] Claim already submitted for invoiceId={} — skipping",
                    invoice.getId()
            );
            return claimRequestRepository
                    .findFirstByFinancialDocumentIdOrderByIdDesc(invoice.getId())
                    .orElse(null);
        }

        ClaimRequestBuilderService.ClaimBuildResult built =
                claimRequestBuilderService.buildForInsuranceInvoice(invoice);

        String uploadName = ensureUniqueUploadName(built.uploadName());
        WaseelClaimRequest claimModel = built.claimRequest();

        WaseelClaimUploadRequest uploadRequest = new WaseelClaimUploadRequest(
                uploadName,
                null,
                List.of(claimModel)
        );
        String requestJson = toJson(uploadRequest);

        ClaimRequest claimRequest = persistDraft(invoice, built, uploadName, requestJson, claimModel);

        try {
            WaseelClaimUploadResponse response = waseelClaimService.uploadClaims(uploadRequest);
            String responseJson = toJson(response);

            claimRequest.setStatus(ClaimStatus.SUBMITTED);
            claimRequest.setUploadId(response == null ? null : response.uploadId());
            claimRequest.setUploadName(response == null ? uploadName : response.uploadName());
            claimRequest.setResponseJson(responseJson);
            claimRequest.setOutcome(resolveOutcome(response));
            claimRequest.setMessage(response == null ? null : response.message());
            claimRequest.setSubmittedAt(Instant.now());

            return claimRequestRepository.save(claimRequest);

        } catch (HttpStatusCodeException ex) {
            String details = buildWaseelFailureMessage(
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    ex.getMessage()
            );
            // Persist FAILED in this transaction and return (do not rethrow — avoids rolling back the audit row).
            return markFailed(claimRequest, requestJson, details);

        } catch (RestClientException ex) {
            String details = "Failed to upload claim to Waseel: " + ex.getMessage();
            return markFailed(claimRequest, requestJson, details);
        }
    }

    @Transactional(readOnly = true)
    public List<ClaimRequest> listByEncounter(Long encounterId) {
        return claimRequestRepository.findByEncounterIdOrderByIdDesc(encounterId);
    }

    private ClaimRequest persistDraft(
            FinancialDocument invoice,
            ClaimRequestBuilderService.ClaimBuildResult built,
            String uploadName,
            String requestJson,
            WaseelClaimRequest claimModel
    ) {
        PreAuthorizationRequest preAuth = built.primaryPreAuthorization();

        String preAuthRefs = claimModel.preAuthRefNo() == null
                ? null
                : String.join(",", claimModel.preAuthRefNo());

        ClaimRequest claimRequest = ClaimRequest.builder()
                .patientId(invoice.getPatientId())
                .encounterId(invoice.getEncounterId())
                .financialDocumentId(invoice.getId())
                .preAuthorizationId(preAuth == null ? null : preAuth.getId())
                .patientInsuranceId(preAuth == null ? null : preAuth.getPatientInsuranceId())
                .uploadName(uploadName)
                .provClaimNo(built.provClaimNo())
                .claimReference(invoice.getClaimReference())
                .preAuthRefNo(preAuthRefs)
                .approvalResponseId(preAuth == null ? null : preAuth.getApprovalResponseId())
                .totalNet(claimModel.totalNet())
                .status(ClaimStatus.SUBMITTING)
                .requestJson(requestJson)
                .build();

        claimRequest = claimRequestRepository.save(claimRequest);

        List<ClaimItem> items = new ArrayList<>();
        List<WaseelApprovalItem> waseelItems = claimModel.items() == null
                ? List.of()
                : claimModel.items();

        for (int i = 0; i < waseelItems.size(); i++) {
            WaseelApprovalItem waseelItem = waseelItems.get(i);
            FinancialDocumentItem invoiceItem =
                    i < built.invoiceItems().size() ? built.invoiceItems().get(i) : null;

            items.add(ClaimItem.builder()
                    .claimRequestId(claimRequest.getId())
                    .sequence(waseelItem.sequence() == null ? i + 1 : waseelItem.sequence())
                    .patientServiceProductId(
                            invoiceItem == null ? null : invoiceItem.getPatientServiceProductId()
                    )
                    .financialDocumentItemId(invoiceItem == null ? null : invoiceItem.getId())
                    .billingChargeLineId(
                            invoiceItem == null ? null : invoiceItem.getBillingChargeLineId()
                    )
                    .itemType(waseelItem.type())
                    .itemCode(waseelItem.itemCode())
                    .itemDescription(waseelItem.itemDescription())
                    .invoiceNo(waseelItem.invoiceNo())
                    .quantity(waseelItem.quantity() == null
                            ? null
                            : BigDecimal.valueOf(waseelItem.quantity()))
                    .unitPrice(waseelItem.unitPrice())
                    .net(waseelItem.net())
                    .patientShare(waseelItem.patientShare())
                    .payerShare(waseelItem.payerShare())
                    .build());
        }

        claimItemRepository.saveAll(items);

        log.info(
                "[CLAIM_SUBMIT] Persisted draft claimId={} encounterId={} invoiceId={} items={}",
                claimRequest.getId(),
                invoice.getEncounterId(),
                invoice.getId(),
                items.size()
        );

        return claimRequest;
    }

    private String ensureUniqueUploadName(String preferred) {
        String name = preferred == null || preferred.isBlank()
                ? "CLM-" + Instant.now().toEpochMilli()
                : preferred.trim();

        for (int attempt = 0; attempt < 5; attempt++) {
            List<Long> existing = waseelClaimService.checkExtractionName(name);
            if (existing == null || existing.isEmpty()) {
                return name;
            }
            name = preferred + "-" + Instant.now().toEpochMilli() + "-" + attempt;
        }
        return name;
    }

    private ClaimRequest markFailed(ClaimRequest claimRequest, String requestJson, String details) {
        claimRequest.setStatus(ClaimStatus.FAILED);
        claimRequest.setRequestJson(requestJson);
        claimRequest.setMessage(details);
        log.error("[CLAIM_SUBMIT] Claim upload failed claimId={} details={}", claimRequest.getId(), details);
        return claimRequestRepository.save(claimRequest);
    }

    private String resolveOutcome(WaseelClaimUploadResponse response) {
        if (response == null) {
            return null;
        }
        if (response.noOfAcceptedClaims() != null && response.noOfAcceptedClaims() > 0) {
            return "ACCEPTED";
        }
        if (response.noOfUploadedClaims() != null && response.noOfUploadedClaims() > 0) {
            return "UPLOADED";
        }
        return "SUBMITTED";
    }

    private String buildWaseelFailureMessage(int statusCode, String responseBody, String fallback) {
        String body = responseBody == null || responseBody.isBlank() ? fallback : responseBody;
        return "Waseel claim upload failed (HTTP " + statusCode + "): " + body;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize claim payload", ex);
        }
    }
}
