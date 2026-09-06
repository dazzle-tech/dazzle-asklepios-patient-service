package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.ClaimItem;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimBatchSubmitResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimSubmissionResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimValidationError;
import com.dazzle.asklepios.integration.waseel.dto.claim.PendingClaimInvoiceResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.dazzle.asklepios.integration.waseel.event.InsuranceInvoiceIssuedEvent;
import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.repository.ClaimItemRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.helper.NphiesPayerHelper;
import com.dazzle.asklepios.service.helper.PayorHelper;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClaimSubmissionService {

    private static final List<ClaimStatus> ACTIVE_STATUSES = List.of(
            ClaimStatus.SUBMITTING,
            ClaimStatus.SUBMITTED,
            ClaimStatus.ACCEPTED
    );

    private static final List<FinancialDocumentStatus> CLAIMABLE_INVOICE_STATUSES = List.of(
            FinancialDocumentStatus.ISSUED,
            FinancialDocumentStatus.PARTIALLY_PAID,
            FinancialDocumentStatus.PAID,
            FinancialDocumentStatus.POSTED
    );

    private final ClaimRequestBuilderService claimRequestBuilderService;
    private final WaseelClaimService waseelClaimService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final ClaimItemRepository claimItemRepository;
    private final ObjectMapper objectMapper;
    private final ClaimPayloadValidationService claimPayloadValidationService;
    private final ClaimStatusRefreshService claimStatusRefreshService;
    private final NphiesPayerHelper nphiesPayerHelper;
    private final PayorHelper payorHelper;
    private final ClaimSubmissionService self;
    private final PatientRepository patientRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final WaseelClaimClassification waseelClaimClassification;

    public ClaimSubmissionService(
            ClaimRequestBuilderService claimRequestBuilderService,
            WaseelClaimService waseelClaimService,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService,
            FinancialDocumentRepository financialDocumentRepository,
            PatientEncounterRepository patientEncounterRepository,
            PatientInsuranceRepository patientInsuranceRepository,
            ClaimRequestRepository claimRequestRepository,
            ClaimItemRepository claimItemRepository,
            ObjectMapper objectMapper,
            ClaimPayloadValidationService claimPayloadValidationService,
            ClaimStatusRefreshService claimStatusRefreshService,
            NphiesPayerHelper nphiesPayerHelper,
            PayorHelper payorHelper,
            @Lazy ClaimSubmissionService self,
            PatientRepository patientRepository,
            FinancialDocumentItemRepository financialDocumentItemRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            WaseelClaimClassification waseelClaimClassification
    ) {
        this.claimRequestBuilderService = claimRequestBuilderService;
        this.waseelClaimService = waseelClaimService;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
        this.financialDocumentRepository = financialDocumentRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.claimRequestRepository = claimRequestRepository;
        this.claimItemRepository = claimItemRepository;
        this.objectMapper = objectMapper;
        this.claimPayloadValidationService = claimPayloadValidationService;
        this.claimStatusRefreshService = claimStatusRefreshService;
        this.nphiesPayerHelper = nphiesPayerHelper;
        this.payorHelper = payorHelper;
        this.self = self;
        this.patientRepository = patientRepository;
        this.financialDocumentItemRepository = financialDocumentItemRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.waseelClaimClassification = waseelClaimClassification;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInsuranceInvoiceIssued(InsuranceInvoiceIssuedEvent event) {
        if (event == null
                || event.encounterId() == null
                || event.financialDocumentId() == null) {
            return;
        }

        log.info(
                "[CLAIM_SUBMIT] Insurance invoice issued for encounterId={} documentId={}. "
                        + "Automatic Waseel claim submission is disabled — submit from Claims screen.",
                event.encounterId(),
                event.financialDocumentId()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClaimRequest submitForInsuranceInvoice(Long financialDocumentId) {
        return submitForInsuranceInvoice(financialDocumentId, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClaimRequest submitForInsuranceInvoice(
            Long financialDocumentId,
            WaseelClaimType claimType,
            WaseelClaimSubType claimSubType
    ) {
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

        WaseelClaimType resolvedType = claimType;
        WaseelClaimSubType resolvedSubType = claimSubType;
        ClaimRequest previous = claimRequestRepository
                .findFirstByFinancialDocumentIdOrderByIdDesc(invoice.getId())
                .orElse(null);
        if (resolvedType == null && previous != null) {
            resolvedType = previous.getClaimType();
        }
        if (resolvedSubType == null && previous != null) {
            resolvedSubType = previous.getClaimSubType();
        }
        if (resolvedType == null) {
            resolvedType = WaseelClaimType.PROFESSIONAL;
        }
        if (resolvedSubType == null) {
            PatientEncounter encounter = patientEncounterRepository.findById(invoice.getEncounterId())
                    .orElse(null);
            resolvedSubType = waseelClaimClassification.resolveSubType(
                    resolvedType,
                    encounter == null ? null : encounter.getEncounterType()
            );
        }

        if (hasActiveClaimForType(invoice.getId(), resolvedType)) {
            log.info(
                    "[CLAIM_SUBMIT] Claim already submitted for invoiceId={} type={} — skipping",
                    invoice.getId(),
                    resolvedType
            );
            return claimRequestRepository
                    .findFirstByFinancialDocumentIdOrderByIdDesc(invoice.getId())
                    .orElse(null);
        }

        ClaimRequestBuilderService.ClaimBuildResult built =
                claimRequestBuilderService.buildForInsuranceInvoice(invoice, resolvedType, resolvedSubType);

        String uploadName = ensureUniqueUploadName(built.uploadName());
        WaseelClaimRequest claimModel = built.claimRequest();

        WaseelClaimUploadRequest uploadRequest = new WaseelClaimUploadRequest(
                uploadName,
                null,
                List.of(claimModel)
        );
        String requestJson = toJson(uploadRequest);

        ClaimRequest claimRequest = persistDraft(invoice, built, uploadName, requestJson, claimModel);

        List<ClaimValidationError> validationErrors = claimPayloadValidationService.validate(claimModel);
        if (!validationErrors.isEmpty()) {
            claimRequest.setValidationErrorsJson(toJson(validationErrors));
            claimRequest.setStatus(ClaimStatus.REJECTED);
            claimRequest.setOutcome("NOT_ACCEPTED");
            claimRequest.setMessage(buildValidationFailureMessage(validationErrors));
            return claimRequestRepository.save(claimRequest);
        }

        try {
            WaseelClaimUploadResponse response = waseelClaimService.uploadClaims(uploadRequest);
            String responseJson = toJson(response);

            applyUploadOutcome(claimRequest, response, responseJson);
            claimRequest.setSubmittedAt(Instant.now());
            claimRequest = claimRequestRepository.save(claimRequest);

            return claimStatusRefreshService.refresh(claimRequest.getId());

        } catch (HttpStatusCodeException ex) {
            String details = buildWaseelFailureMessage(
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    ex.getMessage()
            );
            // Persist FAILED in this transaction and return (do not rethrow — avoids rolling back the audit row).
            return markFailed(claimRequest, requestJson, details);

        } catch (RestClientException ex) {
            String details = "Failed to upload claim to Waseel: " + rootCauseMessage(ex);
            return markFailed(claimRequest, requestJson, details);
        }
    }

    @Transactional(readOnly = true)
    public List<ClaimRequest> listByEncounter(Long encounterId) {
        return claimRequestRepository.findByEncounterIdOrderByIdDesc(encounterId);
    }

    @Transactional(readOnly = true)
    public List<PendingClaimInvoiceResponse> listPendingInsuranceInvoices(
            Long payorId,
            Instant fromDate,
            Instant toDate
    ) {
        return listPendingInsuranceInvoices(payorId, null, fromDate, toDate, null, null);
    }

    @Transactional(readOnly = true)
    public List<PendingClaimInvoiceResponse> listPendingInsuranceInvoices(
            Long payorId,
            String payerNphiesId,
            Instant fromDate,
            Instant toDate
    ) {
        return listPendingInsuranceInvoices(payorId, payerNphiesId, fromDate, toDate, null, null);
    }

    @Transactional(readOnly = true)
    public List<PendingClaimInvoiceResponse> listPendingInsuranceInvoices(
            Long payorId,
            String payerNphiesId,
            Instant fromDate,
            Instant toDate,
            WaseelClaimType claimType,
            WaseelClaimSubType claimSubType
    ) {
        waseelClaimClassification.assertTypeAndSubType(claimType, claimSubType);

        Instant from = fromDate != null ? fromDate : Instant.EPOCH;
        Instant to = toDate != null ? toDate : Instant.parse("9999-12-31T00:00:00Z");
        List<String> nphiesIds = resolvePayerNphiesIds(payorId, payerNphiesId);
        boolean useNphiesFilter = nphiesIds.stream().anyMatch(this::isNphiesCode);
        Long resolvedPayorId = payorId != null ? payorId : -1L;

        log.info(
                "[CLAIM_BATCH] Listing pending invoices payorId={} payerNphiesId={} nphiesIds={} from={} to={} type={} subType={}",
                payorId,
                payerNphiesId,
                nphiesIds,
                from,
                to,
                claimType,
                claimSubType
        );

        List<FinancialDocument> invoices = financialDocumentRepository.findPendingInsuranceClaimInvoices(
                        useNphiesFilter ? 1 : 0,
                        resolvedPayorId,
                        nphiesIds,
                        from,
                        to,
                        CLAIMABLE_INVOICE_STATUSES.stream().map(Enum::name).toList(),
                        ACTIVE_STATUSES.stream().map(Enum::name).toList(),
                        claimType.name()
                )
                .stream()
                .filter(invoice -> belongsToSelectedPayor(invoice, resolvedPayorId, nphiesIds, useNphiesFilter))
                .toList();

        return toPendingClaimInvoiceResponses(invoices, claimType, claimSubType);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClaimBatchSubmitResponse submitBatchForInvoices(
            List<Long> financialDocumentIds,
            WaseelClaimType claimType,
            WaseelClaimSubType claimSubType
    ) {
        waseelClaimClassification.assertTypeAndSubType(claimType, claimSubType);

        if (financialDocumentIds == null || financialDocumentIds.isEmpty()) {
            throw new BadRequestAlertException(
                    "Select at least one insurance invoice to submit.",
                    "claim",
                    "claim.batch.empty"
            );
        }

        List<Long> uniqueIds = financialDocumentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<Long> payorIds = new LinkedHashSet<>();
        Set<String> nphiesKeys = new LinkedHashSet<>();
        List<FinancialDocument> invoices = new ArrayList<>();
        List<ClaimRequestBuilderService.ClaimBuildResult> builds = new ArrayList<>();

        for (Long financialDocumentId : uniqueIds) {
            FinancialDocument invoice = loadClaimableInsuranceInvoice(financialDocumentId);

            if (hasActiveClaimForType(invoice.getId(), claimType)) {
                throw new BadRequestAlertException(
                        "Invoice " + invoice.getDocumentNumber()
                                + " already has an active " + claimType.name() + " claim.",
                        "claim",
                        "claim.alreadySubmitted"
                );
            }

            Long payorId = resolvePayorId(invoice.getEncounterId());
            if (payorId != null) {
                payorIds.add(payorId);
            }

            String nphiesId = resolveEncounterPayerNphiesId(invoice.getEncounterId());
            if (nphiesId != null) {
                nphiesKeys.add(nphiesId);
            }

            invoices.add(invoice);
            builds.add(claimRequestBuilderService.buildForInsuranceInvoice(
                    invoice,
                    claimType,
                    claimSubType
            ));
        }

        if (payorIds.size() > 1 || nphiesKeys.size() > 1) {
            throw new BadRequestAlertException(
                    "All selected invoices must belong to the same payor for a monthly claim batch.",
                    "claim",
                    "claim.batch.payorMismatch"
            );
        }

        String uploadName = ensureUniqueUploadName(
                "CLM-BATCH-" + Instant.now().toEpochMilli()
        );

        List<WaseelClaimRequest> claimModels = builds.stream()
                .map(ClaimRequestBuilderService.ClaimBuildResult::claimRequest)
                .toList();

        WaseelClaimUploadRequest uploadRequest = new WaseelClaimUploadRequest(
                uploadName,
                null,
                claimModels
        );
        String requestJson = toJson(uploadRequest);

        List<ClaimRequest> claimRequests = new ArrayList<>();
        for (int i = 0; i < invoices.size(); i++) {
            claimRequests.add(
                    persistDraft(
                            invoices.get(i),
                            builds.get(i),
                            uploadName,
                            requestJson,
                            claimModels.get(i)
                    )
            );
        }

        List<ClaimValidationError> validationErrors = claimModels.stream()
                .flatMap(model -> claimPayloadValidationService.validate(model).stream())
                .toList();

        if (!validationErrors.isEmpty()) {
            String message = buildValidationFailureMessage(validationErrors);
            for (ClaimRequest claimRequest : claimRequests) {
                claimRequest.setValidationErrorsJson(toJson(validationErrors));
                claimRequest.setStatus(ClaimStatus.REJECTED);
                claimRequest.setOutcome("NOT_ACCEPTED");
                claimRequest.setMessage(message);
                claimRequestRepository.save(claimRequest);
            }

            return toBatchResponse(
                    uploadName,
                    null,
                    "NOT_ACCEPTED",
                    message,
                    claimRequests
            );
        }

        try {
            WaseelClaimUploadResponse response = waseelClaimService.uploadClaims(uploadRequest);
            String responseJson = toJson(response);

            for (ClaimRequest claimRequest : claimRequests) {
                applyUploadOutcome(claimRequest, response, responseJson);
                claimRequest.setSubmittedAt(Instant.now());
                claimRequestRepository.save(claimRequest);
            }

            List<ClaimRequest> refreshedClaims = new ArrayList<>();
            for (ClaimRequest claimRequest : claimRequests) {
                refreshedClaims.add(claimStatusRefreshService.refresh(claimRequest.getId()));
            }

            ClaimRequest first = refreshedClaims.get(0);
            return toBatchResponse(
                    first.getUploadName(),
                    first.getUploadId(),
                    first.getOutcome(),
                    first.getMessage(),
                    refreshedClaims
            );

        } catch (HttpStatusCodeException ex) {
            String details = buildWaseelFailureMessage(
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    ex.getMessage()
            );
            for (ClaimRequest claimRequest : claimRequests) {
                markFailed(claimRequest, requestJson, details);
            }
            return toBatchResponse(uploadName, null, "FAILED", details, claimRequests);

        } catch (RestClientException ex) {
            String details = "Failed to upload claim batch to Waseel: " + rootCauseMessage(ex);
            for (ClaimRequest claimRequest : claimRequests) {
                markFailed(claimRequest, requestJson, details);
            }
            return toBatchResponse(uploadName, null, "FAILED", details, claimRequests);
        }
    }

    private FinancialDocument loadClaimableInsuranceInvoice(Long financialDocumentId) {
        FinancialDocument invoice = financialDocumentRepository.findById(financialDocumentId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Insurance invoice not found",
                        "claim",
                        "invoice.notFound"
                ));

        if (invoice.getDocumentType() != FinancialDocumentType.INVOICE
                || invoice.getDocumentSubtype() != FinancialDocumentSubtype.INSURANCE_CLAIM) {
            throw new BadRequestAlertException(
                    "Only insurance claim invoices can be submitted to Waseel.",
                    "claim",
                    "invoice.subtype.invalid"
            );
        }

        if (!CLAIMABLE_INVOICE_STATUSES.contains(invoice.getStatus())) {
            throw new BadRequestAlertException(
                    "Invoice " + invoice.getDocumentNumber() + " is not finalized for claim submission.",
                    "claim",
                    "invoice.notFinalized"
            );
        }

        if (!encounterInsuranceEligibilityService.isInsuranceEncounter(invoice.getEncounterId())) {
            throw new BadRequestAlertException(
                    "Encounter " + invoice.getEncounterId() + " is not an insurance visit.",
                    "claim",
                    "encounter.notInsurance"
            );
        }

        return invoice;
    }

    private boolean belongsToSelectedPayor(
            FinancialDocument invoice,
            Long selectedPayorId,
            List<String> nphiesIds,
            boolean useNphiesFilter
    ) {
        return patientEncounterRepository.findById(invoice.getEncounterId())
                .map(PatientEncounter::getPatientInsuranceId)
                .flatMap(patientInsuranceRepository::findById)
                .map(insurance -> {
                    if (useNphiesFilter) {
                        String insuranceNphiesId = insurance.getPayerNphiesId();
                        return insuranceNphiesId != null
                                && nphiesIds.contains(insuranceNphiesId.toLowerCase(java.util.Locale.ROOT));
                    }

                    return selectedPayorId != null
                            && selectedPayorId > 0
                            && selectedPayorId.equals(insurance.getPayorId());
                })
                .orElse(false);
    }

    private boolean isNphiesCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.chars().anyMatch(ch -> !Character.isDigit(ch));
    }

    private String resolveEncounterPayerNphiesId(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .map(PatientEncounter::getPatientInsuranceId)
                .flatMap(patientInsuranceRepository::findById)
                .map(PatientInsurance::getPayerNphiesId)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .orElse(null);
    }

    private Long resolvePayorId(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .map(PatientEncounter::getPatientInsuranceId)
                .flatMap(patientInsuranceRepository::findById)
                .map(insurance -> {
                    if (insurance.getPayorId() != null) {
                        return insurance.getPayorId();
                    }

                    NphiesPayerDTO nphiesPayer =
                            nphiesPayerHelper.findByNphiesId(insurance.getPayerNphiesId());
                    return nphiesPayer == null ? null : nphiesPayer.id();
                })
                .orElse(null);
    }

    private List<String> resolvePayerNphiesIds(Long payorId, String payerNphiesId) {
        LinkedHashSet<String> nphiesIds = new LinkedHashSet<>();
        addNphiesId(nphiesIds, payerNphiesId);

        if (payorId != null) {
            nphiesIds.add(String.valueOf(payorId));

            NphiesPayerDTO nphiesPayer = nphiesPayerHelper.findById(payorId);
            addNphiesId(nphiesIds, nphiesPayer == null ? null : nphiesPayer.nphiesId());

            PayorDTO payor = payorHelper.findPayor(payorId, null);
            addNphiesId(nphiesIds, payor == null ? null : payor.nphiesId());
        }

        if (nphiesIds.isEmpty()) {
            return List.of("");
        }

        return nphiesIds.stream()
                .map(id -> id.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private void addNphiesId(Set<String> nphiesIds, String nphiesId) {
        if (nphiesId != null && !nphiesId.isBlank()) {
            nphiesIds.add(nphiesId.trim());
        }
    }

    private List<PendingClaimInvoiceResponse> toPendingClaimInvoiceResponses(
            List<FinancialDocument> invoices,
            WaseelClaimType claimType,
            WaseelClaimSubType claimSubType
    ) {
        if (invoices == null || invoices.isEmpty()) {
            return List.of();
        }

        List<Long> invoiceIds = invoices.stream()
                .map(FinancialDocument::getId)
                .filter(Objects::nonNull)
                .toList();
        List<Long> encounterIds = invoices.stream()
                .map(FinancialDocument::getEncounterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> patientIds = invoices.stream()
                .map(FinancialDocument::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, List<FinancialDocumentItem>> itemsByInvoiceId = financialDocumentItemRepository
                .findByDocument_IdIn(invoiceIds)
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getDocument() == null ? -1L : item.getDocument().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Long> pspIds = itemsByInvoiceId.values().stream()
                .flatMap(List::stream)
                .map(FinancialDocumentItem::getPatientServiceProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, PatientServiceAndProduct> productsById = pspIds.isEmpty()
                ? Map.of()
                : patientServiceAndProductRepository.findAllById(pspIds).stream()
                .filter(product -> product.getId() != null)
                .collect(Collectors.toMap(PatientServiceAndProduct::getId, Function.identity()));

        Map<Long, PatientEncounter> encountersById = encounterIds.isEmpty()
                ? Map.of()
                : patientEncounterRepository.findAllById(encounterIds).stream()
                .filter(encounter -> encounter.getId() != null)
                .collect(Collectors.toMap(PatientEncounter::getId, Function.identity()));

        Map<Long, Patient> patientsById = patientIds.isEmpty()
                ? Map.of()
                : patientRepository.findAllById(patientIds).stream()
                .filter(patient -> patient.getId() != null)
                .collect(Collectors.toMap(Patient::getId, Function.identity()));

        List<PendingClaimInvoiceResponse> results = new ArrayList<>();
        for (FinancialDocument invoice : invoices) {
            PatientEncounter encounter = encountersById.get(invoice.getEncounterId());
            EncounterType encounterType = encounter == null ? null : encounter.getEncounterType();
            if (!waseelClaimClassification.matchesEncounter(encounterType, claimType, claimSubType)) {
                continue;
            }

            List<FinancialDocumentItem> matchingItems = itemsByInvoiceId
                    .getOrDefault(invoice.getId(), List.of())
                    .stream()
                    .filter(item -> waseelClaimClassification.isClaimableForType(
                            item.getPatientServiceProductId() == null
                                    ? null
                                    : productsById.get(item.getPatientServiceProductId()),
                            claimType
                    ))
                    .toList();

            if (matchingItems.isEmpty()) {
                continue;
            }

            BigDecimal matchingNet = matchingItems.stream()
                    .map(FinancialDocumentItem::getInsuranceShareAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (matchingNet.signum() == 0) {
                matchingNet = matchingItems.stream()
                        .map(FinancialDocumentItem::getNetAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            results.add(new PendingClaimInvoiceResponse(
                    invoice.getId(),
                    invoice.getDocumentNumber(),
                    invoice.getEncounterId(),
                    encounter,
                    encounterType,
                    invoice.getPatientId(),
                    patientsById.get(invoice.getPatientId()),
                    resolvePayorId(invoice.getEncounterId()),
                    invoice.getClaimReference(),
                    invoice.getTotalAmount(),
                    invoice.getCurrency() == null ? null : invoice.getCurrency().name(),
                    invoice.getCreatedDate(),
                    claimType,
                    claimSubType,
                    matchingItems.size(),
                    matchingNet.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return results;
    }

    private boolean hasActiveClaimForType(Long financialDocumentId, WaseelClaimType claimType) {
        if (financialDocumentId == null || claimType == null) {
            return false;
        }
        if (claimRequestRepository.existsByFinancialDocumentIdAndClaimTypeAndStatusIn(
                financialDocumentId,
                claimType,
                ACTIVE_STATUSES
        )) {
            return true;
        }
        return claimType == WaseelClaimType.PROFESSIONAL
                && claimRequestRepository.existsByFinancialDocumentIdAndClaimTypeIsNullAndStatusIn(
                financialDocumentId,
                ACTIVE_STATUSES
        );
    }

    private ClaimBatchSubmitResponse toBatchResponse(
            String uploadName,
            Long uploadId,
            String outcome,
            String message,
            List<ClaimRequest> claimRequests
    ) {
        List<ClaimSubmissionResponse> claims = claimRequests.stream()
                .map(this::toSubmissionResponse)
                .toList();

        return new ClaimBatchSubmitResponse(
                uploadName,
                uploadId,
                outcome,
                message,
                claims.size(),
                claims
        );
    }

    private ClaimSubmissionResponse toSubmissionResponse(ClaimRequest claim) {
        return new ClaimSubmissionResponse(
                claim.getId(),
                claim.getEncounterId(),
                claim.getFinancialDocumentId(),
                claim.getPreAuthorizationId(),
                claim.getClaimType(),
                claim.getClaimSubType(),
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
                .claimType(built.claimType())
                .claimSubType(built.claimSubType())
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

    private String rootCauseMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown error";
        }
        StringBuilder message = new StringBuilder(
                ex.getMessage() == null || ex.getMessage().isBlank()
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage().trim()
        );
        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && cause != ex && depth < 5) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && !causeMessage.isBlank() && !message.toString().contains(causeMessage)) {
                message.append(" | ").append(causeMessage.trim());
            }
            cause = cause.getCause();
            depth++;
        }
        return message.toString();
    }

    private void applyUploadOutcome(
            ClaimRequest claimRequest,
            WaseelClaimUploadResponse response,
            String responseJson
    ) {
        claimRequest.setUploadId(response == null ? claimRequest.getUploadId() : response.uploadId());
        claimRequest.setUploadName(response == null ? claimRequest.getUploadName() : response.uploadName());
        claimRequest.setResponseJson(responseJson);
        claimRequest.setMessage(response == null ? null : response.message());

        if (response != null
                && response.noOfNotAcceptedClaims() != null
                && response.noOfNotAcceptedClaims() > 0) {
            claimRequest.setStatus(ClaimStatus.REJECTED);
            claimRequest.setOutcome("NOT_ACCEPTED");
            if (claimRequest.getMessage() == null || claimRequest.getMessage().isBlank()) {
                claimRequest.setMessage("Waseel rejected " + response.noOfNotAcceptedClaims() + " claim(s).");
            }
            return;
        }

        if (response != null
                && response.noOfAcceptedClaims() != null
                && response.noOfAcceptedClaims() > 0) {
            claimRequest.setStatus(ClaimStatus.ACCEPTED);
            claimRequest.setOutcome("ACCEPTED");
            return;
        }

        claimRequest.setStatus(ClaimStatus.SUBMITTED);
        claimRequest.setOutcome(resolveOutcome(response));
    }

    private String buildValidationFailureMessage(List<ClaimValidationError> validationErrors) {
        if (validationErrors == null || validationErrors.isEmpty()) {
            return "Claim validation failed before Waseel upload.";
        }
        ClaimValidationError first = validationErrors.get(0);
        if (validationErrors.size() == 1) {
            return first.message();
        }
        return first.message() + " (" + validationErrors.size() + " validation issue(s) found).";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize claim payload", ex);
        }
    }
}
