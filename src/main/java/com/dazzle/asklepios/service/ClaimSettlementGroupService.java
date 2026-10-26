package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.ClaimSettlement;
import com.dazzle.asklepios.domain.ClaimSettlementInvoice;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementMembershipStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimSubmissionResponse;
import com.dazzle.asklepios.integration.waseel.dto.claim.PendingClaimInvoiceResponse;
import com.dazzle.asklepios.integration.waseel.service.ClaimSubmissionService;
import com.dazzle.asklepios.integration.waseel.service.WaseelClaimClassification;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.ClaimSettlementInvoiceRepository;
import com.dazzle.asklepios.repository.ClaimSettlementRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementCreateRequest;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementGroupDetailResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementGroupSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementInvoiceLineResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementSubmitResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Groups insurance invoices under one statement number before they are submitted to Waseel.
 * A rejected invoice leaves that statement and can be grouped again. Older claims keep the settlement
 * number they already have.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimSettlementGroupService {

    private static final String ENTITY_NAME = "claimSettlement";

    private final ClaimSubmissionService claimSubmissionService;
    private final ClaimSettlementMembershipService claimSettlementMembershipService;
    private final ClaimSettlementNumberService claimSettlementNumberService;
    private final WaseelClaimClassification waseelClaimClassification;
    private final ClaimSettlementRepository claimSettlementRepository;
    private final ClaimSettlementInvoiceRepository claimSettlementInvoiceRepository;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientRepository patientRepository;
    private final ClaimRequestRepository claimRequestRepository;

    @Transactional(readOnly = true)
    public Page<PendingClaimInvoiceResponse> eligibleInvoices(
            Long payorId,
            String payerNphiesId,
            Instant fromDate,
            Instant toDate,
            LocalDate encounterDateFrom,
            LocalDate encounterDateTo,
            WaseelClaimType claimType,
            WaseelClaimSubType claimSubType,
            String medicalRecordNumber,
            String encounterNumber,
            Pageable pageable
    ) {
        return claimSubmissionService.listPendingInsuranceInvoices(
                payorId,
                payerNphiesId,
                fromDate,
                toDate,
                encounterDateFrom,
                encounterDateTo,
                claimType,
                claimSubType,
                medicalRecordNumber,
                encounterNumber,
                pageable
        );
    }

    @Transactional
    public ClaimSettlementGroupDetailResponse create(ClaimSettlementCreateRequest request) {
        if (request == null) {
            throw requiredInvoices();
        }
        waseelClaimClassification.assertTypeAndSubType(request.claimType(), request.claimSubType());
        requireTables();

        List<Long> invoiceIds = distinctIds(request.financialDocumentIds());
        if (invoiceIds.isEmpty()) {
            throw requiredInvoices();
        }

        Map<Long, FinancialDocument> documents = financialDocumentRepository.findAllById(invoiceIds).stream()
                .collect(Collectors.toMap(FinancialDocument::getId, Function.identity()));
        List<FinancialDocument> invoices = new ArrayList<>();
        for (Long invoiceId : invoiceIds) {
            FinancialDocument invoice = documents.get(invoiceId);
            if (invoice == null) {
                throw new BadRequestAlertException(
                        "Invoice " + invoiceId + " was not found.",
                        ENTITY_NAME,
                        "invoice.notFound"
                );
            }
            invoices.add(invoice);
        }

        PayorChoice payor = samePayor(invoices);
        Map<Long, PendingClaimInvoiceResponse> eligible = eligibleById(
                payor,
                request.claimType(),
                request.claimSubType()
        );
        for (FinancialDocument invoice : invoices) {
            PendingClaimInvoiceResponse row = eligible.get(invoice.getId());
            if (row == null) {
                throw new BadRequestAlertException(
                        "Invoice " + label(invoice)
                                + " is not eligible for a statement of account.",
                        ENTITY_NAME,
                        "settlement.invoice.ineligible"
                );
            }
            if (!row.selectable()) {
                throw new BadRequestAlertException(
                        "Invoice " + label(invoice) + " has a test result that is not Result Approved.",
                        ENTITY_NAME,
                        "claim.resultsNotApproved"
                );
            }
        }
        claimSettlementMembershipService.assertNotInActiveSettlement(invoiceIds);

        ClaimSettlement settlement = ClaimSettlement.builder()
                .settlementNo(claimSettlementNumberService.allocateSettlementNo())
                .status(ClaimSettlementStatus.READY_FOR_SUBMISSION)
                .payorId(payor.payorId())
                .payerNphiesId(payor.payerNphiesId())
                .payerName(payor.payerName())
                .claimType(request.claimType())
                .claimSubType(request.claimSubType())
                .invoiceCount(invoiceIds.size())
                .totalInvoiceAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalClaimAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .build();

        try {
            settlement = claimSettlementRepository.saveAndFlush(settlement);
            List<ClaimSettlementInvoice> lines = new ArrayList<>();
            for (Long invoiceId : invoiceIds) {
                lines.add(ClaimSettlementInvoice.builder()
                        .settlementId(settlement.getId())
                        .financialDocumentId(invoiceId)
                        .membershipStatus(ClaimSettlementMembershipStatus.ACTIVE)
                        .build());
            }
            claimSettlementInvoiceRepository.saveAll(lines);
            claimSettlementInvoiceRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestAlertException(
                    "One of the selected invoices was just included in another statement.",
                    ENTITY_NAME,
                    "settlement.alreadyGrouped"
            );
        }

        claimSettlementMembershipService.recalculate(settlement.getId());
        log.info(
                "[CLAIM_SETTLEMENT] Created settlementNo={} invoices={} payorId={}",
                settlement.getSettlementNo(),
                invoiceIds.size(),
                payor.payorId()
        );
        return detail(settlement.getId());
    }

    @Transactional(readOnly = true)
    public Page<ClaimSettlementGroupSummaryResponse> list(
            Long payorId,
            String payerNphiesId,
            String settlementNo,
            ClaimSettlementStatus status,
            Instant fromDate,
            Instant toDate,
            boolean submittableOnly,
            Pageable pageable
    ) {
        Pageable sorted = withDefaultSort(pageable);
        if (!claimSettlementMembershipService.tablesReady()) {
            return new PageImpl<>(List.of(), sorted, 0);
        }
        List<ClaimSettlement> settlements = claimSettlementRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        Map<Long, List<ClaimSettlementInvoice>> linesBySettlement = linesBySettlement(settlements);

        List<ClaimSettlementGroupSummaryResponse> rows = new ArrayList<>();
        for (ClaimSettlement settlement : settlements) {
            if (!matchesPayer(settlement, payorId, payerNphiesId)
                    || !matchesSettlementNo(settlement, settlementNo)
                    || !matchesStatus(settlement, status)
                    || !matchesCreatedDate(settlement, fromDate, toDate)) {
                continue;
            }
            ClaimSettlementGroupSummaryResponse summary = toSummary(
                    settlement,
                    linesBySettlement.getOrDefault(settlement.getId(), List.of())
            );
            if (submittableOnly && !summary.submittable()) {
                continue;
            }
            rows.add(summary);
        }

        int start = (int) Math.min(sorted.getOffset(), rows.size());
        int end = Math.min(start + sorted.getPageSize(), rows.size());
        return new PageImpl<>(List.copyOf(rows.subList(start, end)), sorted, rows.size());
    }

    @Transactional(readOnly = true)
    public ClaimSettlementGroupDetailResponse detail(Long settlementId) {
        requireTables();
        ClaimSettlement settlement = claimSettlementRepository.findById(settlementId)
                .orElseThrow(() -> notFound(settlementId));
        List<ClaimSettlementInvoice> lines = claimSettlementInvoiceRepository.findBySettlementId(settlementId);
        return new ClaimSettlementGroupDetailResponse(toSummary(settlement, lines), toLines(lines));
    }

    public ClaimSettlementSubmitResponse submit(Long settlementId) {
        requireTables();
        ClaimSettlement settlement = claimSettlementRepository.findById(settlementId)
                .orElseThrow(() -> notFound(settlementId));
        claimSettlementMembershipService.reconcileLinkedClaims(settlementId);

        List<Long> invoiceIds = claimSettlementMembershipService.unlinkedActiveDocumentIds(settlementId);
        if (invoiceIds.isEmpty()) {
            throw new BadRequestAlertException(
                    "This statement has no invoices left to submit.",
                    ENTITY_NAME,
                    "settlement.nothingToSubmit"
            );
        }

        List<ClaimRequest> claims = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Long invoiceId : invoiceIds) {
            try {
                ClaimRequest claim = claimSubmissionService.submitForInsuranceInvoice(
                        invoiceId,
                        settlement.getClaimType(),
                        settlement.getClaimSubType(),
                        false
                );
                if (claim == null) {
                    failures.add("Invoice " + invoiceId + " did not produce a claim.");
                    continue;
                }
                claims.add(claim);
            } catch (RuntimeException ex) {
                log.error(
                        "[CLAIM_SETTLEMENT] Submit failed settlementId={} invoiceId={}",
                        settlementId,
                        invoiceId,
                        ex
                );
                failures.add(ex.getMessage() == null ? "Invoice " + invoiceId + " could not be submitted." : ex.getMessage());
            }
        }

        claimSettlementMembershipService.recalculate(settlementId);
        int releasedThisSubmit = (int) claims.stream()
                .filter(claim -> ClaimSettlementRules.leavesSettlement(claim.getStatus()))
                .count();
        log.info(
                "[CLAIM_SETTLEMENT] Submitted settlementNo={} claims={} released={}",
                settlement.getSettlementNo(),
                claims.size(),
                releasedThisSubmit
        );

        return new ClaimSettlementSubmitResponse(
                detail(settlementId).statement(),
                claims.size(),
                releasedThisSubmit,
                submitMessage(claims.size(), releasedThisSubmit, failures),
                claims.stream().map(this::toClaimResponse).toList()
        );
    }

    private Map<Long, PendingClaimInvoiceResponse> eligibleById(
            PayorChoice payor,
            WaseelClaimType claimType,
            WaseelClaimSubType claimSubType
    ) {
        return claimSubmissionService.listPendingInsuranceInvoices(
                        payor.payorId(),
                        payor.payorId() == null ? payor.payerNphiesId() : null,
                        null,
                        null,
                        null,
                        null,
                        claimType,
                        claimSubType,
                        null,
                        null
                )
                .stream()
                .collect(Collectors.toMap(
                        PendingClaimInvoiceResponse::financialDocumentId,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private PayorChoice samePayor(List<FinancialDocument> invoices) {
        Set<Long> encounterIds = invoices.stream()
                .map(FinancialDocument::getEncounterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PatientEncounter> encounters = encounterIds.isEmpty()
                ? Map.of()
                : patientEncounterRepository.findAllById(encounterIds).stream()
                        .collect(Collectors.toMap(PatientEncounter::getId, Function.identity()));

        Set<Long> insuranceIds = encounters.values().stream()
                .map(PatientEncounter::getPatientInsuranceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PatientInsurance> insurances = insuranceIds.isEmpty()
                ? Map.of()
                : patientInsuranceRepository.findAllById(insuranceIds).stream()
                        .collect(Collectors.toMap(PatientInsurance::getId, Function.identity()));

        Set<Long> payorIds = new LinkedHashSet<>();
        Set<String> nphiesIds = new LinkedHashSet<>();
        String payerName = null;
        String payerNphiesId = null;

        for (FinancialDocument invoice : invoices) {
            PatientEncounter encounter = encounters.get(invoice.getEncounterId());
            PatientInsurance insurance = encounter == null || encounter.getPatientInsuranceId() == null
                    ? null
                    : insurances.get(encounter.getPatientInsuranceId());
            if (insurance == null) {
                throw new BadRequestAlertException(
                        "Invoice " + label(invoice) + " is not an insurance invoice.",
                        ENTITY_NAME,
                        "encounter.notInsurance"
                );
            }
            if (insurance.getPayorId() != null) {
                payorIds.add(insurance.getPayorId());
            }
            if (hasText(insurance.getPayerNphiesId())) {
                nphiesIds.add(insurance.getPayerNphiesId().trim().toLowerCase(Locale.ROOT));
                if (payerNphiesId == null) {
                    payerNphiesId = insurance.getPayerNphiesId().trim();
                }
            }
            if (payerName == null && hasText(insurance.getPayerName())) {
                payerName = insurance.getPayerName().trim();
            }
        }

        if (payorIds.size() > 1 || nphiesIds.size() > 1) {
            throw new BadRequestAlertException(
                    "All selected invoices must belong to the same insurance company.",
                    ENTITY_NAME,
                    "settlement.payorMismatch"
            );
        }

        Long payorId = payorIds.isEmpty() ? null : payorIds.iterator().next();
        if (payerName == null) {
            payerName = payerNphiesId;
        }
        return new PayorChoice(payorId, payerNphiesId, payerName);
    }

    private Map<Long, List<ClaimSettlementInvoice>> linesBySettlement(List<ClaimSettlement> settlements) {
        List<Long> settlementIds = settlements.stream().map(ClaimSettlement::getId).toList();
        if (settlementIds.isEmpty()) {
            return Map.of();
        }
        return claimSettlementInvoiceRepository.findBySettlementIdIn(settlementIds).stream()
                .collect(Collectors.groupingBy(ClaimSettlementInvoice::getSettlementId));
    }

    private ClaimSettlementGroupSummaryResponse toSummary(
            ClaimSettlement settlement,
            List<ClaimSettlementInvoice> lines
    ) {
        int active = 0;
        int released = 0;
        int unlinked = 0;
        for (ClaimSettlementInvoice line : lines) {
            if (line.getMembershipStatus() == ClaimSettlementMembershipStatus.RELEASED) {
                released++;
                continue;
            }
            active++;
            if (line.getClaimRequestId() == null) {
                unlinked++;
            }
        }

        return new ClaimSettlementGroupSummaryResponse(
                settlement.getId(),
                settlement.getSettlementNo(),
                settlement.getStatus(),
                settlement.getPayorId(),
                settlement.getPayerNphiesId(),
                settlement.getPayerName(),
                settlement.getClaimType(),
                settlement.getClaimSubType(),
                active,
                released,
                unlinked,
                settlement.getTotalInvoiceAmount(),
                settlement.getTotalClaimAmount(),
                settlement.getCreatedDate(),
                settlement.getCreatedBy(),
                unlinked > 0 && settlement.getStatus() != ClaimSettlementStatus.CANCELLED
        );
    }

    private List<ClaimSettlementInvoiceLineResponse> toLines(List<ClaimSettlementInvoice> lines) {
        List<ClaimSettlementInvoice> ordered = lines.stream()
                .sorted(Comparator
                        .comparing((ClaimSettlementInvoice line) ->
                                line.getMembershipStatus() != ClaimSettlementMembershipStatus.ACTIVE)
                        .thenComparing(ClaimSettlementInvoice::getId))
                .toList();

        Set<Long> documentIds = ordered.stream()
                .map(ClaimSettlementInvoice::getFinancialDocumentId)
                .collect(Collectors.toSet());
        Set<Long> claimIds = ordered.stream()
                .map(ClaimSettlementInvoice::getClaimRequestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, FinancialDocument> documents = documentIds.isEmpty()
                ? Map.of()
                : financialDocumentRepository.findAllById(documentIds).stream()
                        .collect(Collectors.toMap(FinancialDocument::getId, Function.identity()));
        Map<Long, ClaimRequest> claims = claimIds.isEmpty()
                ? Map.of()
                : claimRequestRepository.findAllById(claimIds).stream()
                        .collect(Collectors.toMap(ClaimRequest::getId, Function.identity()));

        Set<Long> encounterIds = documents.values().stream()
                .map(FinancialDocument::getEncounterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> patientIds = documents.values().stream()
                .map(FinancialDocument::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, PatientEncounter> encounters = encounterIds.isEmpty()
                ? Map.of()
                : patientEncounterRepository.findAllById(encounterIds).stream()
                        .collect(Collectors.toMap(PatientEncounter::getId, Function.identity()));
        Map<Long, Patient> patients = patientIds.isEmpty()
                ? Map.of()
                : patientRepository.findAllById(patientIds).stream()
                        .collect(Collectors.toMap(Patient::getId, Function.identity()));

        List<ClaimSettlementInvoiceLineResponse> rows = new ArrayList<>();
        for (ClaimSettlementInvoice line : ordered) {
            FinancialDocument document = documents.get(line.getFinancialDocumentId());
            ClaimRequest claim = line.getClaimRequestId() == null ? null : claims.get(line.getClaimRequestId());
            PatientEncounter encounter = document == null ? null : encounters.get(document.getEncounterId());
            Patient patient = document == null ? null : patients.get(document.getPatientId());
            rows.add(new ClaimSettlementInvoiceLineResponse(
                    line.getFinancialDocumentId(),
                    document == null ? null : document.getDocumentNumber(),
                    document == null ? null : document.getPatientId(),
                    patientName(patient),
                    encounter == null ? null : encounter.getEncounterNumber(),
                    encounter == null || encounter.getEncounterType() == null
                            ? null
                            : encounter.getEncounterType().name(),
                    claim == null ? null : claim.getId(),
                    claim == null ? null : claim.getStatus(),
                    claim == null ? null : firstNonBlank(claim.getClaimReference(), claim.getProvClaimNo()),
                    document == null ? null : document.getTotalAmount(),
                    claim == null ? null : claim.getTotalNet(),
                    line.getMembershipStatus(),
                    line.getReleaseReason(),
                    line.getReleasedAt()
            ));
        }
        return rows;
    }

    private ClaimSubmissionResponse toClaimResponse(ClaimRequest claim) {
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

    private String submitMessage(int submittedCount, int releasedCount, List<String> failures) {
        if (!failures.isEmpty() && submittedCount == 0) {
            return failures.get(0);
        }
        if (!failures.isEmpty()) {
            return "Submitted " + submittedCount + " invoice(s). " + failures.size() + " could not be submitted.";
        }
        if (releasedCount > 0 && releasedCount < submittedCount) {
            return "Submitted the statement. "
                    + releasedCount
                    + " rejected invoice(s) were removed and can be grouped again.";
        }
        if (releasedCount > 0) {
            return "Every submitted invoice was rejected and removed from the statement.";
        }
        return "Statement submitted.";
    }

    private boolean matchesPayer(ClaimSettlement settlement, Long payorId, String payerNphiesId) {
        boolean hasPayor = payorId != null;
        boolean hasNphies = hasText(payerNphiesId);
        if (!hasPayor && !hasNphies) {
            return true;
        }
        if (hasPayor && payorId.equals(settlement.getPayorId())) {
            return true;
        }
        return hasNphies
                && settlement.getPayerNphiesId() != null
                && payerNphiesId.trim().equalsIgnoreCase(settlement.getPayerNphiesId());
    }

    private boolean matchesSettlementNo(ClaimSettlement settlement, String settlementNo) {
        if (!hasText(settlementNo)) {
            return true;
        }
        return settlement.getSettlementNo() != null
                && settlement.getSettlementNo().equalsIgnoreCase(settlementNo.trim());
    }

    private boolean matchesStatus(ClaimSettlement settlement, ClaimSettlementStatus status) {
        return status == null || settlement.getStatus() == status;
    }

    private boolean matchesCreatedDate(ClaimSettlement settlement, Instant fromDate, Instant toDate) {
        Instant created = settlement.getCreatedDate();
        if (fromDate != null && (created == null || created.isBefore(fromDate))) {
            return false;
        }
        return toDate == null || (created != null && created.isBefore(toDate));
    }

    private List<Long> distinctIds(List<Long> financialDocumentIds) {
        if (financialDocumentIds == null) {
            return List.of();
        }
        return financialDocumentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id")
        );
    }

    private String patientName(Patient patient) {
        if (patient == null) {
            return null;
        }
        String fullName = Stream.of(
                        patient.getFirstName(),
                        patient.getSecondName(),
                        patient.getThirdName(),
                        patient.getLastName()
                )
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return hasText(fullName) ? fullName : null;
    }

    private String label(FinancialDocument invoice) {
        if (invoice != null && hasText(invoice.getDocumentNumber())) {
            return invoice.getDocumentNumber().trim();
        }
        return invoice == null ? "unknown" : String.valueOf(invoice.getId());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void requireTables() {
        if (!claimSettlementMembershipService.tablesReady()) {
            throw new BadRequestAlertException(
                    "Statement of account tables are not installed yet.",
                    ENTITY_NAME,
                    "settlement.schema.missing"
            );
        }
    }

    private BadRequestAlertException requiredInvoices() {
        return new BadRequestAlertException(
                "Select at least one invoice.",
                ENTITY_NAME,
                "settlement.invoices.required"
        );
    }

    private NotFoundAlertException notFound(Long settlementId) {
        return new NotFoundAlertException(
                "Statement of account " + settlementId + " was not found.",
                ENTITY_NAME,
                "notfound"
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PayorChoice(Long payorId, String payerNphiesId, String payerName) {
    }
}
