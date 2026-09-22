package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.FinancialDocumentItemStatus;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillableVisitResponse;
import com.dazzle.asklepios.service.dto.billing.BillingEligibilitySnapshotResponse;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterHasInvoiceResponse;
import com.dazzle.asklepios.service.dto.billing.EncounterInvoiceDetailsResponse;
import com.dazzle.asklepios.service.dto.billing.FinancialCloseRequest;
import com.dazzle.asklepios.service.dto.billing.FinancialCloseResult;
import com.dazzle.asklepios.service.dto.billing.GenerateInvoiceRequest;
import com.dazzle.asklepios.service.dto.billing.GenerateInvoiceResult;
import com.dazzle.asklepios.service.dto.billing.PatientFinancialDocumentResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceGenerationService {

    private static final String ENTITY_NAME = "invoiceGeneration";

    private static final EnumSet<FinancialDocumentStatus> FINAL_INVOICE_STATUSES =
            EnumSet.of(
                    FinancialDocumentStatus.ISSUED,
                    FinancialDocumentStatus.PARTIALLY_PAID,
                    FinancialDocumentStatus.PAID,
                    FinancialDocumentStatus.POSTED
            );

    private static final EnumSet<BillingResponsibilityStatus> EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED
            );

    private static final EnumSet<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            EnumSet.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private static final EnumSet<BillingChargeStatus> EXCLUDED_CHARGE_STATUSES =
            EnumSet.of(
                    BillingChargeStatus.CANCELLED,
                    BillingChargeStatus.REVERSED
            );

    private static final EnumSet<PaymentStatus> BLOCKING_PAYMENT_STATUSES =
            EnumSet.of(
                    PaymentStatus.PENDING,
                    PaymentStatus.SKIPPED_PENDING_PRE_AUTH
            );

    private static final EnumSet<BillingPaymentStatus> RECEIPT_PAYMENT_STATUSES =
            EnumSet.of(
                    BillingPaymentStatus.COMPLETED
            );

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;
    private final BillingChargeRepository billingChargeRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingChargeResponsibilityRepository billingChargeResponsibilityRepository;
    private final WaseelEligibilityRequestRepository waseelEligibilityRequestRepository;
    private final EncounterBillingSummaryService encounterBillingSummaryService;
    private final BillingEligibilitySnapshotService billingEligibilitySnapshotService;
    private final FinancialDocumentNumberAssignmentService documentNumberAssignmentService;
    private final BillingPaymentRepository billingPaymentRepository;
    private final InvoiceApplicableOnAdjustmentService invoiceApplicableOnAdjustmentService;
    private final InvoiceItemPricingSnapshotService invoiceItemPricingSnapshotService;
    private final InvoiceChargePaymentSyncService invoiceChargePaymentSyncService;

    private final EncounterChargeLineEnsuringService encounterChargeLineEnsuringService;
    private final EncounterBillingGuardService encounterBillingGuardService;

    @Transactional(readOnly = true)
    public List<BillableVisitResponse> findBillableVisits(Long patientId) {
        validatePatientId(patientId);

        Page<PatientEncounter> encounters =
                patientEncounterRepository.findByPatientIdOrderByCreatedDateDesc(
                        patientId,
                        Pageable.unpaged()
                );

        return encounters.getContent().stream()
                .map(this::mapBillableVisit)
                .filter(BillableVisitResponse::eligibleForBilling)
                .toList();
    }

    @Transactional(readOnly = true)
    public EncounterHasInvoiceResponse hasInvoice(Long encounterId) {
        requireEncounter(encounterId);

        boolean hasInvoice =
                financialDocumentRepository.existsByEncounterIdAndDocumentType(
                        encounterId,
                        FinancialDocumentType.INVOICE
                );

        return new EncounterHasInvoiceResponse(encounterId, hasInvoice);
    }

    @Transactional(readOnly = true)
    public EncounterInvoiceDetailsResponse getEncounterInvoiceDetails(Long encounterId) {
        PatientEncounter encounter = requireEncounter(encounterId);
        Patient patient = requirePatient(encounter);

        EncounterBillingSummary billingSummary =
                encounterBillingSummaryService.getByEncounterId(encounterId);

        BillingEligibilitySnapshotResponse eligibilitySnapshot =
                billingEligibilitySnapshotService
                        .findByEncounterId(encounterId)
                        .orElse(null);

        String eligibilityReference =
                eligibilitySnapshot != null
                        ? eligibilitySnapshot.eligibilityResponseId()
                        : resolveLiveEligibilityReference(encounter);

        String coverageType = resolveCoverageType(encounterId);
        boolean eligibilityFreezeRequired =
                "INSURANCE".equals(coverageType)
                        && billingEligibilitySnapshotService.isEligibilityFreezeRequired(encounterId);

        return new EncounterInvoiceDetailsResponse(
                encounter.getId(),
                encounter.getEncounterNumber(),
                encounter.getEncounterDate(),
                encounter.getDepartmentId(),
                encounter.getEncounterType(),
                resolveBillingStatus(encounter),
                encounter.getFinanciallyClosedAt(),
                encounter.getFinanciallyClosedBy(),
                coverageType,
                encounter.getPatientInsuranceId(),
                eligibilityReference,
                eligibilitySnapshot,
                mapPatientHeader(patient),
                billingSummary,
                eligibilityFreezeRequired
        );
    }

    @Transactional(readOnly = true)
    public List<PatientFinancialDocumentResponse> listPatientInvoices(Long patientId) {
        validatePatientId(patientId);

        return financialDocumentRepository
                .findAllByPatientIdAndDocumentTypeOrderByCreatedDateDesc(
                        patientId,
                        FinancialDocumentType.INVOICE
                )
                .stream()
                .map(this::mapFinancialDocument)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PatientFinancialDocumentResponse> listPatientFinancialDocuments(
            Long patientId
    ) {
        validatePatientId(patientId);

        List<PatientFinancialDocumentResponse> documents = new ArrayList<>(
                financialDocumentRepository
                        .findAllByPatientIdOrderByCreatedDateDesc(patientId)
                        .stream()
                        .map(this::mapFinancialDocument)
                        .toList()
        );

        documents.addAll(
                billingPaymentRepository
                        .findAllByPatient_IdAndStatusInOrderByPaymentDateDescIdDesc(
                                patientId,
                                RECEIPT_PAYMENT_STATUSES
                        )
                        .stream()
                        .map(this::mapBillingPaymentReceipt)
                        .toList()
        );

        documents.sort(
                Comparator.comparing(
                        PatientFinancialDocumentResponse::createdDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
        );

        return enrichDocumentsWithEncounterNumbers(documents);
    }

    private List<PatientFinancialDocumentResponse> enrichDocumentsWithEncounterNumbers(
            List<PatientFinancialDocumentResponse> documents
    ) {
        Set<Long> encounterIds =
                documents.stream()
                        .map(PatientFinancialDocumentResponse::encounterId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        if (encounterIds.isEmpty()) {
            return documents;
        }

        Map<Long, String> encounterNumbersById =
                patientEncounterRepository.findAllById(encounterIds).stream()
                        .collect(Collectors.toMap(
                                PatientEncounter::getId,
                                PatientEncounter::getEncounterNumber
                        ));

        return documents.stream()
                .map(document -> withEncounterNumber(
                        document,
                        document.encounterId() != null
                                ? encounterNumbersById.get(document.encounterId())
                                : null
                ))
                .toList();
    }

    private PatientFinancialDocumentResponse withEncounterNumber(
            PatientFinancialDocumentResponse document,
            String encounterNumber
    ) {
        return new PatientFinancialDocumentResponse(
                document.id(),
                document.documentNumber(),
                document.documentType(),
                document.documentSubtype(),
                document.status(),
                document.patientId(),
                document.encounterId(),
                document.totalAmount(),
                document.currency(),
                document.eligibilityReference(),
                document.claimReference(),
                document.createdDate(),
                document.parentDocumentId(),
                document.adjustmentReason(),
                document.billingPaymentId(),
                encounterNumber
        );
    }

    @Transactional
    public FinancialCloseResult financialClose(
            Long encounterId,
            FinancialCloseRequest request
    ) {
        PatientEncounter encounter = requireEncounter(encounterId);

        encounterBillingGuardService
                .requireClinicallyCompleteForFinancialSettlement(encounter);

        if (resolveBillingStatus(encounter) == EncounterBillingStatus.INVOICED) {
            throw new BadRequestAlertException(
                    "Encounter is already invoiced.",
                    ENTITY_NAME,
                    "encounter.alreadyInvoiced"
            );
        }

        if (resolveBillingStatus(encounter) == EncounterBillingStatus.FINANCIALLY_CLOSED) {
            throw new BadRequestAlertException(
                    "Encounter is already financially closed.",
                    ENTITY_NAME,
                    "encounter.alreadyFinanciallyClosed"
            );
        }

        long serviceCount =
                patientServiceAndProductRepository.countByEncounterId(encounterId);

        if (serviceCount == 0) {
            throw new BadRequestAlertException(
                    "Encounter has no billable services.",
                    ENTITY_NAME,
                    "encounter.noBillableServices"
            );
        }

        List<PatientServiceAndProduct> encounterServices =
                patientServiceAndProductRepository.findByEncounterId(encounterId);

        for (PatientServiceAndProduct service : encounterServices) {
            if (isFullyCoveredPendingItem(service)) {
                service.setPaymentStatus(PaymentStatus.PAID);
                patientServiceAndProductRepository.save(service);
            }
        }

        List<PatientServiceAndProduct> pendingServices =
                encounterServices.stream()
                        .filter(service ->
                                BLOCKING_PAYMENT_STATUSES.contains(
                                        service.getPaymentStatus()
                                )
                        )
                        .filter(service -> !syncCancelledBillingItem(service))
                        .toList();

        if (!pendingServices.isEmpty()) {
            throw new BadRequestAlertException(
                    "Encounter has pending services that must be resolved before financial closure.",
                    ENTITY_NAME,
                    "encounter.pendingServices"
            );
        }

        if (patientServiceAndProductRepository.existsByEncounterIdAndPreAuthorizationStatus(
                encounterId,
                PreAuthorizationStatus.PENDING_APPROVAL
        )) {
            throw new BadRequestAlertException(
                    "Encounter has pre-authorization items still pending payer approval. "
                            + "Refresh status from Waseel and wait for the final response before closing.",
                    ENTITY_NAME,
                    "encounter.preAuthorization.pending"
            );
        }

        if ("INSURANCE".equals(resolveCoverageType(encounterId))) {
            billingEligibilitySnapshotService.requireReadyForFinancialClose(encounterId);
        }

        if (hasFinalInvoice(encounterId)) {
            throw new BadRequestAlertException(
                    "Encounter already has a final invoice.",
                    ENTITY_NAME,
                    "encounter.finalInvoice.exists"
            );
        }

        String closedBy =
                SecurityUtils.getCurrentUserLogin()
                        .orElse("system");

        Instant closedAt = Instant.now();

        encounter.setBillingStatus(EncounterBillingStatus.FINANCIALLY_CLOSED);
        encounter.setFinanciallyClosedAt(closedAt);
        encounter.setFinanciallyClosedBy(closedBy);
        patientEncounterRepository.save(encounter);

        billingChargeRepository
                .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                        encounterId,
                        EXCLUDED_CHARGE_STATUSES
                )
                .ifPresent(charge -> {
                    if (charge.getStatus() != BillingChargeStatus.CLOSED) {
                        charge.setStatus(BillingChargeStatus.CLOSED);
                        charge.setClosedDate(closedAt);
                        billingChargeRepository.save(charge);
                    }
                });

        return new FinancialCloseResult(
                encounterId,
                EncounterBillingStatus.FINANCIALLY_CLOSED,
                closedAt,
                closedBy
        );
    }

    @Transactional
    public GenerateInvoiceResult generateInvoices(
            Long encounterId,
            GenerateInvoiceRequest request
    ) {
        PatientEncounter encounter = requireEncounter(encounterId);
        Patient patient = requirePatient(encounter);

        encounterBillingGuardService
                .requireClinicallyCompleteForFinancialSettlement(encounter);

        if (resolveBillingStatus(encounter) == EncounterBillingStatus.INVOICED) {
            throw new BadRequestAlertException(
                    "Encounter is already invoiced.",
                    ENTITY_NAME,
                    "encounter.alreadyInvoiced"
            );
        }

        encounterChargeLineEnsuringService.ensureEncounterChargeLines(
                encounterId,
                request.requestId()
        );

        syncFinancialCloseFromCharge(encounter);

        if (!isReadyForInvoicing(encounter, encounterId)) {
            throw new BadRequestAlertException(
                    "Encounter must be financially closed before invoice generation.",
                    ENTITY_NAME,
                    "encounter.notFinanciallyClosed"
            );
        }

        String coverageType = resolveCoverageType(encounterId);
        String eligibilityReference = resolveEligibilityReference(encounterId, encounter);

        List<BillingChargeLine> chargeLines =
                billingChargeLineRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_LINE_STATUSES
                        );

        if (chargeLines.isEmpty()) {
            throw new BadRequestAlertException(
                    "Encounter has no billable charge lines. Complete billing checkout first.",
                    ENTITY_NAME,
                    "encounter.noChargeLines"
            );
        }

        List<PatientFinancialDocumentResponse> createdInvoices =
                new ArrayList<>();

        BigDecimal totalPatientShare =
                chargeLines.stream()
                        .map(BillingChargeLine::getPatientResponsibilityAmount)
                        .map(this::money)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInsuranceShare =
                chargeLines.stream()
                        .map(BillingChargeLine::getInsuranceResponsibilityAmount)
                        .map(this::money)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if ("INSURANCE".equals(coverageType)) {
            if (totalPatientShare.signum() > 0) {
                createdInvoices.add(
                        mapFinancialDocument(
                                createInvoiceDocument(
                                        encounter,
                                        patient,
                                        chargeLines,
                                        FinancialDocumentSubtype.PATIENT,
                                        eligibilityReference,
                                        null
                                )
                        )
                );
            }

            if (totalInsuranceShare.signum() > 0) {
                String claimReference =
                        "CLM-"
                                + encounter.getEncounterNumber()
                                + "-"
                                + Instant.now().toEpochMilli();

                FinancialDocument insuranceInvoice = createInvoiceDocument(
                        encounter,
                        patient,
                        chargeLines,
                        FinancialDocumentSubtype.INSURANCE_CLAIM,
                        eligibilityReference,
                        claimReference
                );

                createdInvoices.add(mapFinancialDocument(insuranceInvoice));
            }
        } else {
            if (hasSubtypeFinalInvoice(
                    encounterId,
                    FinancialDocumentSubtype.PATIENT
            )) {
                throw new BadRequestAlertException(
                        "Patient invoice already exists for this encounter.",
                        ENTITY_NAME,
                        "invoice.patient.exists"
                );
            }

            createdInvoices.add(
                    mapFinancialDocument(
                            createInvoiceDocument(
                                    encounter,
                                    patient,
                                    chargeLines,
                                    FinancialDocumentSubtype.PATIENT,
                                    null,
                                    null
                            )
                    )
            );
        }

        if (createdInvoices.isEmpty()) {
            throw new BadRequestAlertException(
                    "No invoice amounts were found for this encounter.",
                    ENTITY_NAME,
                    "invoice.noAmounts"
            );
        }

        encounter.setBillingStatus(EncounterBillingStatus.INVOICED);
        patientEncounterRepository.save(encounter);

        return new GenerateInvoiceResult(
                encounterId,
                EncounterBillingStatus.INVOICED,
                coverageType,
                createdInvoices
        );
    }

    private FinancialDocument createInvoiceDocument(
            PatientEncounter encounter,
            Patient patient,
            List<BillingChargeLine> chargeLines,
            FinancialDocumentSubtype subtype,
            String eligibilityReference,
            String claimReference
    ) {
        if (hasSubtypeFinalInvoice(encounter.getId(), subtype)) {
            throw new BadRequestAlertException(
                    "A final invoice already exists for subtype "
                            + subtype.name(),
                    ENTITY_NAME,
                    "invoice.subtype.exists"
            );
        }

        FinancialDocument document =
                FinancialDocument.builder()
                        .documentType(FinancialDocumentType.INVOICE)
                        .documentSubtype(subtype)
                        .status(FinancialDocumentStatus.ISSUED)
                        .patientId(patient.getId())
                        .encounterId(encounter.getId())
                        .totalAmount(BigDecimal.ZERO)
                        .currency(chargeLines.get(0).getCurrency())
                        .eligibilityReference(eligibilityReference)
                        .claimReference(claimReference)
                        .createdDate(Instant.now())
                        .build();

        document.setDocumentNumber(
                documentNumberAssignmentService.requireNextDocumentNumber(
                        encounter.getFacilityId(),
                        FinancialDocumentType.INVOICE,
                        LocalDate.now()
                )
        );

        document = financialDocumentRepository.save(document);

        List<FinancialDocumentItem> items =
                buildInvoiceItems(document, chargeLines, subtype);

        if (items.isEmpty()) {
            throw new BadRequestAlertException(
                    "No invoice line items could be generated for subtype "
                            + subtype.name(),
                    ENTITY_NAME,
                    "invoice.items.empty"
            );
        }

        invoiceItemPricingSnapshotService.captureChargeLineSnapshots(items);

        items = invoiceApplicableOnAdjustmentService.applyApplicableOnAdjustments(
                items,
                encounter.getFacilityId(),
                document.getCurrency(),
                LocalDate.now()
        );

        invoiceChargePaymentSyncService.syncPreInvoicePayments(items);

        financialDocumentItemRepository.saveAll(items);

        BigDecimal totalAmount =
                items.stream()
                        .map(FinancialDocumentItem::getNetAmount)
                        .map(this::money)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        document.setTotalAmount(totalAmount);
        return financialDocumentRepository.save(document);
    }

    private List<FinancialDocumentItem> buildInvoiceItems(
            FinancialDocument document,
            List<BillingChargeLine> chargeLines,
            FinancialDocumentSubtype subtype
    ) {
        List<FinancialDocumentItem> items = new ArrayList<>();

        for (BillingChargeLine line : chargeLines) {
            BigDecimal shareAmount =
                    subtype == FinancialDocumentSubtype.INSURANCE_CLAIM
                            ? money(line.getInsuranceResponsibilityAmount())
                            : money(line.getPatientResponsibilityAmount());

            if (subtype == FinancialDocumentSubtype.PATIENT
                    && shareAmount.signum() == 0
                    && document.getEligibilityReference() == null) {
                shareAmount = money(line.getNetAmount());
            }

            if (shareAmount.signum() <= 0) {
                continue;
            }

            BigDecimal lineNet = money(line.getNetAmount());
            BigDecimal grossAmount =
                    proportional(line.getGrossAmount(), shareAmount, lineNet);
            BigDecimal discountAmount =
                    proportional(line.getDiscountAmount(), shareAmount, lineNet);
            BigDecimal taxAmount =
                    proportional(line.getTaxAmount(), shareAmount, lineNet);

            BigDecimal patientShare =
                    subtype == FinancialDocumentSubtype.PATIENT
                            ? shareAmount
                            : BigDecimal.ZERO;
            BigDecimal insuranceShare =
                    subtype == FinancialDocumentSubtype.INSURANCE_CLAIM
                            ? shareAmount
                            : BigDecimal.ZERO;

            items.add(
                    FinancialDocumentItem.builder()
                            .document(document)
                            .patientServiceProductId(
                                    line.getPatientServiceProduct().getId()
                            )
                            .billingChargeLineId(line.getId())
                            .itemCode(line.getItemCode())
                            .itemDescription(line.getItemDescription())
                            .quantity(
                                    line.getQuantity()
                                            .setScale(0, RoundingMode.HALF_UP)
                                            .longValue()
                            )
                            .unitPrice(
                                    shareAmount.divide(
                                            line.getQuantity(),
                                            4,
                                            RoundingMode.HALF_UP
                                    )
                            )
                            .grossAmount(grossAmount)
                            .discountAmount(discountAmount)
                            .taxAmount(taxAmount)
                            .netAmount(shareAmount)
                            .patientShareAmount(patientShare)
                            .insuranceShareAmount(insuranceShare)
                            .paidAmount(BigDecimal.ZERO)
                            .remainingAmount(shareAmount)
                            .insurancePaidAmount(BigDecimal.ZERO)
                            .insuranceRemainingAmount(insuranceShare)
                            .status(FinancialDocumentItemStatus.PENDING)
                            .currency(line.getCurrency())
                            .build()
            );
        }

        return items;
    }

    private boolean isReadyForInvoicing(
            PatientEncounter encounter,
            Long encounterId
    ) {
        if (resolveBillingStatus(encounter) == EncounterBillingStatus.FINANCIALLY_CLOSED) {
            return true;
        }

        return billingChargeRepository
                .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                        encounterId,
                        EXCLUDED_CHARGE_STATUSES
                )
                .map(charge -> charge.getStatus() == BillingChargeStatus.CLOSED)
                .orElse(false);
    }

    private void syncFinancialCloseFromCharge(PatientEncounter encounter) {
        if (resolveBillingStatus(encounter) == EncounterBillingStatus.INVOICED
                || resolveBillingStatus(encounter) == EncounterBillingStatus.FINANCIALLY_CLOSED) {
            return;
        }

        billingChargeRepository
                .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                        encounter.getId(),
                        EXCLUDED_CHARGE_STATUSES
                )
                .filter(charge -> charge.getStatus() == BillingChargeStatus.CLOSED)
                .ifPresent(charge -> {
                    Instant closedAt =
                            charge.getClosedDate() != null
                                    ? charge.getClosedDate()
                                    : Instant.now();

                    encounter.setBillingStatus(EncounterBillingStatus.FINANCIALLY_CLOSED);
                    encounter.setFinanciallyClosedAt(closedAt);
                    encounter.setFinanciallyClosedBy(
                            SecurityUtils.getCurrentUserLogin()
                                    .orElse("checkout")
                    );
                    patientEncounterRepository.save(encounter);
                });
    }

    private boolean hasSubtypeFinalInvoice(
            Long encounterId,
            FinancialDocumentSubtype subtype
    ) {
        return financialDocumentRepository
                .existsByEncounterIdAndDocumentTypeAndDocumentSubtypeAndStatusIn(
                        encounterId,
                        FinancialDocumentType.INVOICE,
                        subtype,
                        FINAL_INVOICE_STATUSES
                );
    }

    private BigDecimal proportional(
            BigDecimal amount,
            BigDecimal share,
            BigDecimal whole
    ) {
        if (amount == null || share == null || whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        return money(
                amount.multiply(share)
                        .divide(whole, 4, RoundingMode.HALF_UP)
        );
    }

    private BillableVisitResponse mapBillableVisit(PatientEncounter encounter) {
        Long encounterId = encounter.getId();
        long serviceCount =
                patientServiceAndProductRepository.countByEncounterId(encounterId);
        boolean hasFinalInvoice = hasFinalInvoice(encounterId);
        EncounterBillingStatus billingStatus = resolveBillingStatus(encounter);

        BillingCharge charge =
                billingChargeRepository
                        .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                                encounterId,
                                EXCLUDED_CHARGE_STATUSES
                        )
                        .orElse(null);

        BigDecimal netAmount =
                charge == null
                        ? BigDecimal.ZERO
                        : money(charge.getNetAmount());

        String currency =
                charge != null && charge.getCurrency() != null
                        ? charge.getCurrency().name()
                        : null;

        boolean chargeClosed =
                charge != null
                        && charge.getStatus() == BillingChargeStatus.CLOSED;

        boolean invoiceReady =
                (billingStatus == EncounterBillingStatus.FINANCIALLY_CLOSED
                        || chargeClosed)
                        && !hasFinalInvoice;

        boolean eligibleForBilling =
                serviceCount > 0
                        && !hasFinalInvoice
                        && billingStatus != EncounterBillingStatus.INVOICED;

        return new BillableVisitResponse(
                encounterId,
                encounter.getEncounterNumber(),
                encounter.getEncounterDate(),
                encounter.getDepartmentId(),
                encounter.getEncounterType(),
                resolveCoverageType(encounterId),
                billingStatus,
                charge == null ? null : charge.getStatus(),
                (int) serviceCount,
                netAmount,
                currency,
                hasFinalInvoice,
                invoiceReady,
                eligibleForBilling
        );
    }

    private PatientFinancialDocumentResponse mapFinancialDocument(
            FinancialDocument document
    ) {
        return new PatientFinancialDocumentResponse(
                document.getId(),
                document.getDocumentNumber(),
                document.getDocumentType(),
                document.getDocumentSubtype(),
                document.getStatus(),
                document.getPatientId(),
                document.getEncounterId(),
                money(document.getTotalAmount()),
                document.getCurrency(),
                document.getEligibilityReference(),
                document.getClaimReference(),
                document.getCreatedDate(),
                document.getParentDocumentId(),
                document.getAdjustmentReason(),
                null,
                null
        );
    }

    private PatientFinancialDocumentResponse mapBillingPaymentReceipt(
            BillingPayment payment
    ) {
        String documentNumber =
                payment.getReceiptNumber() != null
                        && !payment.getReceiptNumber().isBlank()
                        ? payment.getReceiptNumber()
                        : payment.getPaymentNumber();

        Long encounterId =
                payment.getEncounter() != null
                        ? payment.getEncounter().getId()
                        : null;

        return new PatientFinancialDocumentResponse(
                payment.getId(),
                documentNumber,
                FinancialDocumentType.RECEIPT,
                FinancialDocumentSubtype.PATIENT,
                FinancialDocumentStatus.ISSUED,
                payment.getPatient().getId(),
                encounterId,
                money(payment.getAmount()),
                payment.getCurrency(),
                null,
                null,
                payment.getPaymentDate(),
                null,
                null,
                payment.getId(),
                payment.getEncounter() != null
                        ? payment.getEncounter().getEncounterNumber()
                        : null
        );
    }

    private EncounterInvoiceDetailsResponse.PatientInvoiceHeader mapPatientHeader(
            Patient patient
    ) {
        String fullName =
                String.join(
                        " ",
                        Objects.toString(patient.getFirstName(), "").trim(),
                        Objects.toString(patient.getLastName(), "").trim()
                ).trim();

        return new EncounterInvoiceDetailsResponse.PatientInvoiceHeader(
                patient.getId(),
                patient.getMedicalRecordNumber(),
                fullName,
                patient.getDocumentId(),
                patient.getPrimaryMobileNumber()
        );
    }

    private boolean hasFinalInvoice(Long encounterId) {
        String coverageType = resolveCoverageType(encounterId);

        if ("INSURANCE".equals(coverageType)) {
            boolean hasPatient =
                    hasSubtypeFinalInvoice(
                            encounterId,
                            FinancialDocumentSubtype.PATIENT
                    );
            boolean hasInsurance =
                    hasSubtypeFinalInvoice(
                            encounterId,
                            FinancialDocumentSubtype.INSURANCE_CLAIM
                    );

            BigDecimal totalPatientShare =
                    billingChargeLineRepository
                            .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                    encounterId,
                                    EXCLUDED_LINE_STATUSES
                            )
                            .stream()
                            .map(BillingChargeLine::getPatientResponsibilityAmount)
                            .map(this::money)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalInsuranceShare =
                    billingChargeLineRepository
                            .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                    encounterId,
                                    EXCLUDED_LINE_STATUSES
                            )
                            .stream()
                            .map(BillingChargeLine::getInsuranceResponsibilityAmount)
                            .map(this::money)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean patientSatisfied =
                    totalPatientShare.signum() == 0 || hasPatient;
            boolean insuranceSatisfied =
                    totalInsuranceShare.signum() == 0 || hasInsurance;

            return patientSatisfied && insuranceSatisfied;
        }

        return hasSubtypeFinalInvoice(
                encounterId,
                FinancialDocumentSubtype.PATIENT
        );
    }

    private String resolveCoverageType(Long encounterId) {
        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_RESPONSIBILITY_STATUSES
                        );

        boolean hasInsurance =
                responsibilities.stream()
                        .anyMatch(responsibility ->
                                responsibility.getResponsiblePartyType()
                                        == ResponsiblePartyType.INSURANCE
                        );

        return hasInsurance ? "INSURANCE" : "SELF_PAY";
    }

    private String resolveEligibilityReference(
            Long encounterId,
            PatientEncounter encounter
    ) {
        return billingEligibilitySnapshotService
                .resolveEligibilityReference(encounterId)
                .orElseGet(() -> resolveLiveEligibilityReference(encounter));
    }

    private String resolveLiveEligibilityReference(PatientEncounter encounter) {
        if (encounter.getPatient() == null) {
            return null;
        }

        return waseelEligibilityRequestRepository
                .findTopByPatientIdAndRequestStatusOrderByRespondedAtDesc(
                        encounter.getPatient().getId(),
                        "SUCCESS"
                )
                .or(() ->
                        waseelEligibilityRequestRepository
                                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                        encounter.getPatient().getId(),
                                        "SUCCESS"
                                )
                )
                .map(WaseelEligibilityRequest::getEligibilityResponseId)
                .orElse(null);
    }

    private boolean isFullyCoveredPendingItem(PatientServiceAndProduct service) {
        if (service == null || service.getPaymentStatus() != PaymentStatus.PENDING) {
            return false;
        }

        BigDecimal patientShare =
                service.getPatientShareAmount() == null
                        ? BigDecimal.ZERO
                        : service.getPatientShareAmount();
        BigDecimal remaining =
                service.getRemainingAmount() == null
                        ? BigDecimal.ZERO
                        : service.getRemainingAmount();

        return patientShare.signum() == 0 && remaining.signum() == 0;
    }

    /**
     * Charge-line cancellation is the financial source of truth. If the latest
     * line is already cancelled/reversed, persist that onto the operational item
     * so financial close and Service & Product stay in sync.
     */
    private boolean syncCancelledBillingItem(PatientServiceAndProduct service) {
        if (service == null || service.getId() == null) {
            return false;
        }

        boolean cancelledLine =
                billingChargeLineRepository
                        .findFirstByPatientServiceProduct_IdOrderByIdDesc(
                                service.getId()
                        )
                        .map(line ->
                                EXCLUDED_LINE_STATUSES.contains(
                                        line.getStatus()
                                )
                        )
                        .orElse(false);

        if (!cancelledLine) {
            return false;
        }

        if (service.getPaymentStatus() != PaymentStatus.CANCELLED) {
            service.setPaymentStatus(PaymentStatus.CANCELLED);
            patientServiceAndProductRepository.save(service);
        }

        return true;
    }

    private EncounterBillingStatus resolveBillingStatus(PatientEncounter encounter) {
        if (encounter.getBillingStatus() != null) {
            return encounter.getBillingStatus();
        }
        return EncounterBillingStatus.OPEN;
    }

    private PatientEncounter requireEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found with id " + encounterId,
                                ENTITY_NAME,
                                "encounter.notfound"
                        )
                );
    }

    private Patient requirePatient(PatientEncounter encounter) {
        Patient patient = encounter.getPatient();
        if (patient == null || patient.getId() == null) {
            throw new BadRequestAlertException(
                    "Encounter patient is missing.",
                    ENTITY_NAME,
                    "encounter.patient.missing"
            );
        }
        return patient;
    }

    private void validatePatientId(Long patientId) {
        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patientId.required"
            );
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : value.setScale(4, RoundingMode.HALF_UP);
    }
}
