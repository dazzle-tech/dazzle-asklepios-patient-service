package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingLedger;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryDirection;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerTransactionType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.PayerType;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.BillingLedgerRepository;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.BillingPaymentTransactionRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.AuditRow;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.ClaimFinancial;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.CoveragePayer;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.FinalSettlement;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.Header;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.InsuranceSplit;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.InvoiceBreakdown;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.PatientSettlement;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.ReceiptRow;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.ServiceLine;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.SettlementParty;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.StatementFooter;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.TimelineRow;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.VisitPatient;
import com.dazzle.asklepios.service.dto.accounting.PatientFinancialDashboardResponse;
import com.dazzle.asklepios.service.dto.accounting.PatientFinancialDashboardResponse.DashboardTotals;
import com.dazzle.asklepios.service.dto.accounting.PatientFinancialDashboardResponse.EncounterFinancialRow;
import com.dazzle.asklepios.service.dto.billing.BillingEligibilitySnapshotResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientFinancialStatementService {

    private static final String ENTITY_NAME = "patientFinancialStatement";
    private static final int MONEY_SCALE = 4;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Riyadh");

    private static final EnumSet<BillingChargeStatus> EXCLUDED_CHARGE_STATUSES =
            EnumSet.of(BillingChargeStatus.CANCELLED, BillingChargeStatus.REVERSED);

    private static final EnumSet<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            EnumSet.of(BillingChargeLineStatus.CANCELLED, BillingChargeLineStatus.REVERSED);

    private static final EnumSet<BillingResponsibilityStatus> EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED,
                    BillingResponsibilityStatus.SUPERSEDED
            );

    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final BillingChargeRepository billingChargeRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingChargeResponsibilityRepository billingChargeResponsibilityRepository;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final BillingPaymentRepository billingPaymentRepository;
    private final BillingPaymentTransactionRepository billingPaymentTransactionRepository;
    private final BillingLedgerRepository billingLedgerRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final BillingWalletService billingWalletService;
    private final BillingEligibilitySnapshotService billingEligibilitySnapshotService;

    public PatientFinancialDashboardResponse getPatientDashboard(Long patientId) {
        Patient patient = requirePatient(patientId);

        List<PatientEncounter> encounters =
                patientEncounterRepository.findAllByPatientIdOrderByCreatedDateDesc(patientId);

        List<BillingCharge> charges =
                billingChargeRepository.findAllByPatient_IdAndStatusNotInOrderByIdAsc(
                        patientId,
                        EXCLUDED_CHARGE_STATUSES
                );

        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository.findAllByPatient_IdAndStatusNotInOrderByIdAsc(
                        patientId,
                        EXCLUDED_RESPONSIBILITY_STATUSES
                );

        List<FinancialDocument> documents =
                financialDocumentRepository.findAllByPatientIdOrderByCreatedDateDesc(patientId);

        List<ClaimRequest> claims =
                claimRequestRepository.findByPatientIdOrderByIdDesc(patientId);

        Map<Long, BillingCharge> chargeByEncounter = latestChargeByEncounter(charges);
        Map<Long, List<BillingChargeResponsibility>> responsibilitiesByEncounter =
                groupResponsibilitiesByEncounter(responsibilities);
        Map<Long, FinancialDocument> patientInvoiceByEncounter =
                latestPatientInvoiceByEncounter(documents);
        Map<Long, ClaimRequest> claimByEncounter = latestClaimByEncounter(claims);

        List<EncounterFinancialRow> rows = new ArrayList<>();
        BigDecimal grossCharges = zero();
        BigDecimal patientResponsibility = zero();
        BigDecimal insuranceShare = zero();
        BigDecimal totalCollected = zero();
        BigDecimal outstanding = zero();

        for (PatientEncounter encounter : encounters) {
            Long encounterId = encounter.getId();
            BillingCharge charge = chargeByEncounter.get(encounterId);
            PartyTotals patientTotals = partyTotals(
                    responsibilitiesByEncounter.getOrDefault(encounterId, List.of()),
                    ResponsiblePartyType.PATIENT
            );
            PartyTotals insuranceTotals = partyTotals(
                    responsibilitiesByEncounter.getOrDefault(encounterId, List.of()),
                    ResponsiblePartyType.INSURANCE
            );

            BigDecimal encounterGross = money(charge == null ? null : charge.getGrossAmount());
            BigDecimal encounterCollected = money(patientTotals.allocated.add(insuranceTotals.allocated));
            BigDecimal encounterOutstanding = money(
                    patientTotals.outstanding.add(insuranceTotals.outstanding)
            );

            FinancialDocument invoice = patientInvoiceByEncounter.get(encounterId);
            ClaimRequest claim = claimByEncounter.get(encounterId);

            rows.add(
                    new EncounterFinancialRow(
                            encounterId,
                            encounter.getEncounterNumber(),
                            encounter.getEncounterDate(),
                            encounter.getEncounterTime(),
                            resolveEncounterInstant(encounter),
                            enumName(encounter.getEncounterType()),
                            encounter.getFacilityId(),
                            encounter.getDepartmentId(),
                            encounter.getPractitionerId(),
                            enumName(encounter.getCoverageType()),
                            encounterGross,
                            patientTotals.total,
                            insuranceTotals.total,
                            encounterCollected,
                            encounterOutstanding,
                            resolveVisitFinancialStatus(
                                    patientTotals,
                                    insuranceTotals
                            ),
                            invoice == null ? null : invoice.getDocumentNumber(),
                            invoice == null ? null : invoice.getId(),
                            claim == null ? null : firstNonBlank(claim.getProvClaimNo(), claim.getClaimReference()),
                            claim == null ? null : claim.getId()
                    )
            );

            grossCharges = grossCharges.add(encounterGross);
            patientResponsibility = patientResponsibility.add(patientTotals.total);
            insuranceShare = insuranceShare.add(insuranceTotals.total);
            totalCollected = totalCollected.add(encounterCollected);
            outstanding = outstanding.add(encounterOutstanding);
        }

        return new PatientFinancialDashboardResponse(
                patient.getId(),
                patient.getMedicalRecordNumber(),
                fullName(patient),
                patient.getDocumentId(),
                resolveCurrency(charges),
                new DashboardTotals(
                        money(grossCharges),
                        money(patientResponsibility),
                        money(insuranceShare),
                        money(totalCollected),
                        money(outstanding)
                ),
                rows
        );
    }

    public EncounterFinancialStatementResponse getEncounterStatement(Long encounterId) {
        PatientEncounter encounter = requireEncounter(encounterId);
        Patient patient = requirePatient(encounter);

        List<BillingChargeLine> lines =
                billingChargeLineRepository.findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                        encounterId,
                        EXCLUDED_LINE_STATUSES
                );

        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository.findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                        encounterId,
                        EXCLUDED_RESPONSIBILITY_STATUSES
                );

        List<FinancialDocument> documents =
                financialDocumentRepository.findAllByEncounterId(encounterId);

        List<ClaimRequest> claims =
                claimRequestRepository.findByEncounterIdOrderByIdDesc(encounterId);

        List<BillingPayment> payments =
                billingPaymentRepository.findAllByEncounter_IdOrderByIdAsc(encounterId);

        List<BillingLedger> ledgerEntries =
                billingLedgerRepository.findAllByEncounter_IdOrderByTransactionDateAscIdAsc(encounterId);

        BillingCharge charge = resolveCharge(lines);
        BillingWallet wallet = billingWalletService.findOptionalByPatient(patient.getId());
        BillingEligibilitySnapshotResponse eligibility =
                billingEligibilitySnapshotService.findByEncounterId(encounterId).orElse(null);
        PatientInsurance insurance = resolveInsurance(encounter, responsibilities, eligibility);
        ClaimRequest claim = claims.isEmpty() ? null : claims.get(0);
        FinancialDocument patientInvoice = latestDocument(
                documents,
                FinancialDocumentType.INVOICE,
                FinancialDocumentSubtype.PATIENT
        );
        FinancialDocument anyInvoice = patientInvoice != null
                ? patientInvoice
                : latestDocument(documents, FinancialDocumentType.INVOICE, null);

        PartyTotals patientTotals = partyTotals(responsibilities, ResponsiblePartyType.PATIENT);
        PartyTotals insuranceTotals = partyTotals(responsibilities, ResponsiblePartyType.INSURANCE);
        ComponentTotals components = componentTotals(responsibilities);

        BigDecimal gross = money(charge == null ? null : charge.getGrossAmount());
        BigDecimal discount = money(charge == null ? null : charge.getDiscountAmount());
        BigDecimal net = money(charge == null ? null : charge.getNetAmount());
        BigDecimal vat = money(charge == null ? null : charge.getTaxAmount());
        BigDecimal invoiceTotal = anyInvoice == null
                ? money(net.add(vat))
                : money(anyInvoice.getTotalAmount());
        BigDecimal patientBilled = patientInvoice == null
                ? money(patientTotals.total.add(vat))
                : money(patientInvoice.getTotalAmount());
        BigDecimal walletReserved = sumReserved(lines);
        BigDecimal refunds = sumRefunds(payments);

        String visitFinancialStatus = resolveVisitFinancialStatus(patientTotals, insuranceTotals);
        String patientPaymentStatus = resolvePatientPaymentStatus(patientTotals);
        String insurancePaymentStatus = resolveInsurancePaymentStatus(insuranceTotals, claim);
        String overallSettlement = resolveOverallSettlement(patientTotals, insuranceTotals);
        String statementLifecycle = resolveStatementLifecycle(encounter, charge, anyInvoice);

        Map<Long, List<BillingChargeResponsibility>> responsibilitiesByLine =
                responsibilities.stream()
                        .filter(item -> item.getChargeLine() != null && item.getChargeLine().getId() != null)
                        .collect(Collectors.groupingBy(item -> item.getChargeLine().getId()));

        List<ServiceLine> serviceLines = lines.stream()
                .map(line -> mapServiceLine(
                        line,
                        responsibilitiesByLine.getOrDefault(line.getId(), List.of())
                ))
                .toList();

        Map<Long, String> paymentMethods = loadPaymentMethods(payments);

        List<ReceiptRow> receipts = payments.stream()
                .filter(payment -> payment.getStatus() == BillingPaymentStatus.COMPLETED
                        || payment.getStatus() == BillingPaymentStatus.REFUNDED)
                .sorted(Comparator.comparing(BillingPayment::getPaymentDate).reversed())
                .map(payment -> mapReceipt(payment, paymentMethods))
                .toList();

        List<TimelineRow> timeline = buildTimeline(ledgerEntries);
        List<AuditRow> auditTrail = buildAuditTrail(documents, payments, ledgerEntries, charge);

        BigDecimal eligibleAmount = money(
                insuranceTotals.total
                        .add(components.deductible)
                        .add(components.copay)
        );

        Header header = new Header(
                encounter.getId(),
                encounter.getEncounterNumber(),
                fullName(patient),
                patient.getMedicalRecordNumber(),
                resolveEncounterInstant(encounter),
                enumName(encounter.getEncounterType()),
                visitFinancialStatus,
                anyInvoice == null ? null : anyInvoice.getDocumentNumber(),
                anyInvoice == null ? null : anyInvoice.getId(),
                resolveCurrency(charge, wallet),
                gross,
                vat,
                patientBilled,
                patientTotals.allocated,
                patientTotals.outstanding
        );

        VisitPatient visitPatient = new VisitPatient(
                fullName(patient),
                patient.getMedicalRecordNumber(),
                patient.getDocumentId(),
                encounter.getFacilityId(),
                encounter.getDepartmentId(),
                encounter.getPractitionerId(),
                enumName(encounter.getEncounterType()),
                resolveEncounterInstant(encounter),
                encounter.getEncounterDate(),
                encounter.getEncounterTime(),
                anyInvoice == null ? null : anyInvoice.getDocumentNumber()
        );

        CoveragePayer coveragePayer = mapCoverage(
                encounter,
                insurance,
                eligibility,
                claim,
                responsibilities,
                visitFinancialStatus
        );

        InvoiceBreakdown invoiceBreakdown = new InvoiceBreakdown(
                gross,
                discount,
                net,
                net,
                vat,
                invoiceTotal
        );

        PatientSettlement patientSettlement = new PatientSettlement(
                patientBilled,
                patientTotals.allocated,
                walletReserved,
                refunds,
                patientTotals.outstanding
        );

        InsuranceSplit insuranceSplit = new InsuranceSplit(
                eligibleAmount,
                components.deductible,
                components.copay,
                insuranceTotals.total,
                components.nonCovered,
                insuranceTotals.total
        );

        ClaimFinancial claimFinancial = mapClaim(claim, insuranceTotals);

        FinalSettlement finalSettlement = new FinalSettlement(
                gross,
                patientBilled,
                insuranceTotals.total,
                patientTotals.allocated,
                insuranceTotals.allocated,
                money(patientTotals.allocated.add(insuranceTotals.allocated)),
                patientTotals.outstanding,
                insuranceTotals.outstanding,
                money(patientTotals.outstanding.add(insuranceTotals.outstanding)),
                visitFinancialStatus,
                List.of(
                        new SettlementParty(
                                "Patient",
                                patientBilled,
                                patientTotals.allocated,
                                patientTotals.outstanding
                        ),
                        new SettlementParty(
                                "Insurance",
                                insuranceTotals.total,
                                insuranceTotals.allocated,
                                insuranceTotals.outstanding
                        ),
                        new SettlementParty(
                                "Total",
                                money(patientBilled.add(insuranceTotals.total)),
                                money(patientTotals.allocated.add(insuranceTotals.allocated)),
                                money(patientTotals.outstanding.add(insuranceTotals.outstanding))
                        )
                )
        );

        StatementFooter footer = new StatementFooter(
                charge == null ? encounter.getCreatedBy() : charge.getCreatedBy(),
                encounter.getFinanciallyClosedBy(),
                encounter.getFinanciallyClosedAt(),
                patientPaymentStatus,
                insurancePaymentStatus,
                overallSettlement,
                statementLifecycle,
                Instant.now()
        );

        return new EncounterFinancialStatementResponse(
                header,
                visitPatient,
                coveragePayer,
                serviceLines,
                invoiceBreakdown,
                patientSettlement,
                insuranceSplit,
                receipts,
                claimFinancial,
                timeline,
                finalSettlement,
                auditTrail,
                footer
        );
    }

    private Patient requirePatient(Long patientId) {
        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patientId.required"
            );
        }

        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        ENTITY_NAME,
                        "patient.notfound"
                ));
    }

    private Patient requirePatient(PatientEncounter encounter) {
        if (encounter.getPatient() == null || encounter.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Encounter patient is missing.",
                    ENTITY_NAME,
                    "encounter.patient.missing"
            );
        }

        return requirePatient(encounter.getPatient().getId());
    }

    private PatientEncounter requireEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounterId.required"
            );
        }

        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + encounterId,
                        ENTITY_NAME,
                        "encounter.notfound"
                ));
    }

    private Map<Long, BillingCharge> latestChargeByEncounter(List<BillingCharge> charges) {
        Map<Long, BillingCharge> latest = new LinkedHashMap<>();
        for (BillingCharge charge : charges) {
            if (charge.getEncounter() == null || charge.getEncounter().getId() == null) {
                continue;
            }
            Long encounterId = charge.getEncounter().getId();
            BillingCharge existing = latest.get(encounterId);
            if (existing == null || charge.getId() > existing.getId()) {
                latest.put(encounterId, charge);
            }
        }
        return latest;
    }

    private Map<Long, List<BillingChargeResponsibility>> groupResponsibilitiesByEncounter(
            List<BillingChargeResponsibility> responsibilities
    ) {
        return responsibilities.stream()
                .filter(item -> item.getEncounter() != null && item.getEncounter().getId() != null)
                .collect(Collectors.groupingBy(item -> item.getEncounter().getId()));
    }

    private Map<Long, FinancialDocument> latestPatientInvoiceByEncounter(
            List<FinancialDocument> documents
    ) {
        Map<Long, FinancialDocument> latest = new LinkedHashMap<>();
        for (FinancialDocument document : documents) {
            if (document.getDocumentType() != FinancialDocumentType.INVOICE) {
                continue;
            }
            if (document.getDocumentSubtype() != null
                    && document.getDocumentSubtype() != FinancialDocumentSubtype.PATIENT) {
                continue;
            }
            FinancialDocument existing = latest.get(document.getEncounterId());
            if (existing == null || document.getId() > existing.getId()) {
                latest.put(document.getEncounterId(), document);
            }
        }
        return latest;
    }

    private Map<Long, ClaimRequest> latestClaimByEncounter(List<ClaimRequest> claims) {
        Map<Long, ClaimRequest> latest = new LinkedHashMap<>();
        for (ClaimRequest claim : claims) {
            ClaimRequest existing = latest.get(claim.getEncounterId());
            if (existing == null || claim.getId() > existing.getId()) {
                latest.put(claim.getEncounterId(), claim);
            }
        }
        return latest;
    }

    private BillingCharge resolveCharge(List<BillingChargeLine> lines) {
        return lines.stream()
                .map(BillingChargeLine::getCharge)
                .filter(charge -> charge != null && charge.getId() != null)
                .max(Comparator.comparing(BillingCharge::getId))
                .orElse(null);
    }

    private PatientInsurance resolveInsurance(
            PatientEncounter encounter,
            List<BillingChargeResponsibility> responsibilities,
            BillingEligibilitySnapshotResponse eligibility
    ) {
        Long insuranceId = encounter.getPatientInsuranceId();
        if (insuranceId == null && eligibility != null) {
            insuranceId = eligibility.patientInsuranceId();
        }
        if (insuranceId == null) {
            insuranceId = responsibilities.stream()
                    .map(BillingChargeResponsibility::getPatientInsurance)
                    .filter(Objects::nonNull)
                    .map(PatientInsurance::getId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        if (insuranceId == null) {
            return null;
        }
        return patientInsuranceRepository.findById(insuranceId).orElse(null);
    }

    private FinancialDocument latestDocument(
            List<FinancialDocument> documents,
            FinancialDocumentType type,
            FinancialDocumentSubtype subtype
    ) {
        return documents.stream()
                .filter(document -> document.getDocumentType() == type)
                .filter(document -> subtype == null || document.getDocumentSubtype() == subtype)
                .max(Comparator.comparing(FinancialDocument::getId))
                .orElse(null);
    }

    private ServiceLine mapServiceLine(
            BillingChargeLine line,
            List<BillingChargeResponsibility> lineResponsibilities
    ) {
        PartyTotals patientTotals = partyTotals(lineResponsibilities, ResponsiblePartyType.PATIENT);
        PartyTotals insuranceTotals = partyTotals(lineResponsibilities, ResponsiblePartyType.INSURANCE);
        ComponentTotals components = componentTotals(lineResponsibilities);

        return new ServiceLine(
                line.getId(),
                line.getItemCode(),
                line.getItemDescription(),
                toDecimal(line.getQuantity()),
                money(line.getUnitPrice()),
                money(line.getGrossAmount()),
                money(line.getDiscountAmount()),
                money(line.getNetAmount()),
                components.deductible,
                components.copay,
                components.nonCovered,
                patientTotals.total,
                insuranceTotals.total,
                money(line.getTaxAmount()),
                money(money(line.getNetAmount()).add(money(line.getTaxAmount())))
        );
    }

    private CoveragePayer mapCoverage(
            PatientEncounter encounter,
            PatientInsurance insurance,
            BillingEligibilitySnapshotResponse eligibility,
            ClaimRequest claim,
            List<BillingChargeResponsibility> responsibilities,
            String financialStatus
    ) {
        String payerName = insurance == null ? null : firstNonBlank(insurance.getPayerName(), insurance.getTpaName());
        String memberId = eligibility == null
                ? insurance == null ? null : insurance.getMemberCardId()
                : firstNonBlank(eligibility.memberId(), insurance == null ? null : insurance.getMemberCardId());
        String policyNumber = eligibility == null
                ? insurance == null ? null : insurance.getPolicyNumber()
                : firstNonBlank(eligibility.policyNumber(), insurance == null ? null : insurance.getPolicyNumber());
        String eligibilityValue = eligibility == null
                ? insurance == null ? null : firstNonBlank(insurance.getEligibilityStatus(), insurance.getInforce())
                : firstNonBlank(eligibility.coverageStatus(), eligibility.inforce());
        String authorization = responsibilities.stream()
                .map(BillingChargeResponsibility::getPreAuthorizationReferenceNo)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);

        return new CoveragePayer(
                payerName,
                enumName(encounter.getCoverageType()),
                memberId,
                policyNumber,
                eligibilityValue,
                authorization,
                claim == null ? null : firstNonBlank(claim.getProvClaimNo(), claim.getClaimReference()),
                claim == null ? null : claim.getId(),
                financialStatus
        );
    }

    private ClaimFinancial mapClaim(ClaimRequest claim, PartyTotals insuranceTotals) {
        if (claim == null) {
            return new ClaimFinancial(
                    null,
                    null,
                    "NOT_SUBMITTED",
                    null,
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    insuranceTotals.allocated,
                    insuranceTotals.outstanding
            );
        }

        ClaimStatus status = claim.getStatus();
        BigDecimal submitted = isSubmitted(status) ? money(claim.getTotalNet()) : zero();
        BigDecimal approved = status == ClaimStatus.ACCEPTED ? money(claim.getTotalNet()) : zero();
        BigDecimal rejected = status == ClaimStatus.REJECTED ? money(claim.getTotalNet()) : zero();

        return new ClaimFinancial(
                claim.getId(),
                firstNonBlank(claim.getProvClaimNo(), claim.getClaimReference()),
                status == null ? "NOT_SUBMITTED" : status.name(),
                claim.getMessage(),
                submitted,
                approved,
                rejected,
                zero(),
                approved,
                insuranceTotals.allocated,
                insuranceTotals.outstanding
        );
    }

    private ReceiptRow mapReceipt(
            BillingPayment payment,
            Map<Long, String> paymentMethods
    ) {
        return new ReceiptRow(
                payment.getPaymentDate(),
                firstNonBlank(payment.getReceiptNumber(), payment.getPaymentNumber()),
                firstNonBlank(
                        paymentMethods.get(payment.getId()),
                        payment.getPaymentCategory() == null ? null : payment.getPaymentCategory().name()
                ),
                payment.getPayerType() == null ? "Patient" : formatParty(payment.getPayerType()),
                payment.getStatus() == null ? null : payment.getStatus().name(),
                money(payment.getAmount())
        );
    }

    private Map<Long, String> loadPaymentMethods(List<BillingPayment> payments) {
        List<Long> paymentIds = payments.stream()
                .map(BillingPayment::getId)
                .filter(Objects::nonNull)
                .toList();
        if (paymentIds.isEmpty()) {
            return Map.of();
        }

        return billingPaymentTransactionRepository
                .findAllByPayment_IdInOrderByTransactionDateAscIdAsc(paymentIds)
                .stream()
                .filter(transaction -> transaction.getPayment() != null
                        && transaction.getPayment().getId() != null
                        && hasText(transaction.getPaymentMethodCode()))
                .collect(
                        Collectors.toMap(
                                transaction -> transaction.getPayment().getId(),
                                BillingPaymentTransaction::getPaymentMethodCode,
                                (first, ignored) -> first
                        )
                );
    }

    private List<TimelineRow> buildTimeline(List<BillingLedger> entries) {
        BigDecimal running = zero();
        List<TimelineRow> rows = new ArrayList<>();

        for (BillingLedger entry : entries) {
            BigDecimal debit = entry.getEntryDirection() == BillingLedgerEntryDirection.DEBIT
                    ? money(entry.getAmount())
                    : zero();
            BigDecimal credit = entry.getEntryDirection() == BillingLedgerEntryDirection.CREDIT
                    ? money(entry.getAmount())
                    : zero();
            running = money(running.add(debit).subtract(credit));

            rows.add(
                    new TimelineRow(
                            entry.getTransactionDate(),
                            entry.getTransactionType() == null
                                    ? null
                                    : entry.getTransactionType().name(),
                            firstNonBlank(entry.getReferenceNumber(), entry.getLedgerNumber()),
                            debit,
                            credit,
                            running
                    )
            );
        }

        return rows;
    }

    private List<AuditRow> buildAuditTrail(
            List<FinancialDocument> documents,
            List<BillingPayment> payments,
            List<BillingLedger> ledgerEntries,
            BillingCharge charge
    ) {
        List<AuditRow> rows = new ArrayList<>();

        for (FinancialDocument document : documents) {
            if (document.getDocumentType() == FinancialDocumentType.CREDIT_NOTE
                    || document.getDocumentType() == FinancialDocumentType.DEBIT_NOTE
                    || document.getStatus() == FinancialDocumentStatus.CANCELLED) {
                rows.add(
                        new AuditRow(
                                document.getCreatedDate(),
                                document.getDocumentType() == null
                                        ? document.getStatus() == null ? "ADJUSTMENT" : document.getStatus().name()
                                        : document.getDocumentType().name(),
                                document.getDocumentNumber(),
                                null,
                                null,
                                money(document.getTotalAmount()).toPlainString(),
                                document.getAdjustmentReason()
                        )
                );
            }
        }

        for (BillingPayment payment : payments) {
            if (payment.getStatus() == BillingPaymentStatus.CANCELLED
                    || payment.getStatus() == BillingPaymentStatus.REFUNDED) {
                rows.add(
                        new AuditRow(
                                payment.getPaymentDate(),
                                payment.getStatus().name(),
                                firstNonBlank(payment.getReceiptNumber(), payment.getPaymentNumber()),
                                payment.getCreatedBy(),
                                null,
                                money(payment.getAmount()).toPlainString(),
                                payment.getNotes()
                        )
                );
            }
        }

        for (BillingLedger entry : ledgerEntries) {
            BillingLedgerTransactionType type = entry.getTransactionType();
            if (type == BillingLedgerTransactionType.ADJUSTMENT
                    || type == BillingLedgerTransactionType.REVERSAL
                    || type == BillingLedgerTransactionType.REFUND_COMPLETED
                    || type == BillingLedgerTransactionType.REFUND_REQUESTED
                    || type == BillingLedgerTransactionType.PAYMENT_CANCELLED) {
                rows.add(
                        new AuditRow(
                                entry.getTransactionDate(),
                                type.name(),
                                firstNonBlank(entry.getReferenceNumber(), entry.getLedgerNumber()),
                                entry.getCreatedBy(),
                                null,
                                money(entry.getAmount()).toPlainString(),
                                firstNonBlank(entry.getReason(), entry.getDescription())
                        )
                );
            }
        }

        if (charge != null && charge.getCancelledDate() != null) {
            rows.add(
                    new AuditRow(
                            charge.getCancelledDate(),
                            "CHARGE_CANCELLED",
                            charge.getChargeNumber(),
                            charge.getCancelledBy(),
                            null,
                            money(charge.getGrossAmount()).toPlainString(),
                            charge.getCancellationReason()
                    )
            );
        }

        rows.sort(Comparator.comparing(AuditRow::eventDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private PartyTotals partyTotals(
            List<BillingChargeResponsibility> responsibilities,
            ResponsiblePartyType partyType
    ) {
        BigDecimal total = zero();
        BigDecimal allocated = zero();
        BigDecimal outstanding = zero();

        for (BillingChargeResponsibility responsibility : responsibilities) {
            if (responsibility.getResponsiblePartyType() != partyType) {
                continue;
            }
            total = total.add(money(responsibility.getResponsibilityAmount()));
            allocated = allocated.add(money(responsibility.getAllocatedAmount()));
            outstanding = outstanding.add(money(responsibility.getOutstandingAmount()));
        }

        return new PartyTotals(money(total), money(allocated), money(outstanding));
    }

    private ComponentTotals componentTotals(List<BillingChargeResponsibility> responsibilities) {
        BigDecimal deductible = zero();
        BigDecimal copay = zero();
        BigDecimal nonCovered = zero();

        for (BillingChargeResponsibility responsibility : responsibilities) {
            deductible = deductible.add(money(responsibility.getDeductibleAmount()));
            copay = copay.add(money(responsibility.getCopayAmount()));
            nonCovered = nonCovered.add(money(responsibility.getNonCoveredAmount()));
        }

        return new ComponentTotals(money(deductible), money(copay), money(nonCovered));
    }

    private BigDecimal sumReserved(List<BillingChargeLine> lines) {
        return money(
                lines.stream()
                        .map(BillingChargeLine::getReservedAmount)
                        .map(this::money)
                        .reduce(zero(), BigDecimal::add)
        );
    }

    private BigDecimal sumRefunds(List<BillingPayment> payments) {
        return money(
                payments.stream()
                        .filter(payment -> payment.getStatus() == BillingPaymentStatus.REFUNDED)
                        .map(BillingPayment::getAmount)
                        .map(this::money)
                        .reduce(zero(), BigDecimal::add)
        );
    }

    private String resolveVisitFinancialStatus(PartyTotals patient, PartyTotals insurance) {
        boolean patientClear = patient.outstanding.compareTo(BigDecimal.ZERO) == 0;
        boolean insuranceClear = insurance.outstanding.compareTo(BigDecimal.ZERO) == 0;
        boolean hasInsurance = insurance.total.compareTo(BigDecimal.ZERO) > 0;

        if (patientClear && insuranceClear) {
            return "SETTLED";
        }
        if (patientClear && hasInsurance && !insuranceClear) {
            return "INSURANCE_PENDING";
        }
        if (!patientClear && insuranceClear) {
            return patient.allocated.compareTo(BigDecimal.ZERO) > 0
                    ? "PARTIALLY_SETTLED"
                    : "PATIENT_UNPAID";
        }
        return "PARTIALLY_SETTLED";
    }

    private String resolvePatientPaymentStatus(PartyTotals patient) {
        if (patient.total.compareTo(BigDecimal.ZERO) <= 0
                && patient.outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return "FULLY_PAID";
        }
        if (patient.outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return "FULLY_PAID";
        }
        if (patient.allocated.compareTo(BigDecimal.ZERO) > 0) {
            return "PARTIALLY_PAID";
        }
        return "UNPAID";
    }

    private String resolveInsurancePaymentStatus(PartyTotals insurance, ClaimRequest claim) {
        if (claim != null && claim.getStatus() == ClaimStatus.REJECTED) {
            return "REJECTED";
        }
        if (claim == null || claim.getStatus() == ClaimStatus.DRAFT || claim.getStatus() == ClaimStatus.FAILED) {
            return insurance.total.compareTo(BigDecimal.ZERO) > 0 ? "NOT_SUBMITTED" : "NOT_SUBMITTED";
        }
        if (claim.getStatus() == ClaimStatus.SUBMITTING || claim.getStatus() == ClaimStatus.SUBMITTED) {
            return "SUBMITTED";
        }
        if (insurance.outstanding.compareTo(BigDecimal.ZERO) <= 0 && insurance.total.compareTo(BigDecimal.ZERO) > 0) {
            return "FULLY_PAID";
        }
        if (insurance.allocated.compareTo(BigDecimal.ZERO) > 0) {
            return "PARTIALLY_PAID";
        }
        return "PENDING_PAYMENT";
    }

    private String resolveOverallSettlement(PartyTotals patient, PartyTotals insurance) {
        if (patient.outstanding.compareTo(BigDecimal.ZERO) == 0
                && insurance.outstanding.compareTo(BigDecimal.ZERO) == 0) {
            return "FULLY_SETTLED";
        }
        return "PARTIALLY_SETTLED";
    }

    private String resolveStatementLifecycle(
            PatientEncounter encounter,
            BillingCharge charge,
            FinancialDocument invoice
    ) {
        if (invoice != null && invoice.getStatus() == FinancialDocumentStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (charge != null && charge.getStatus() == BillingChargeStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (encounter.getFinanciallyClosedAt() != null
                || (invoice != null && invoice.getStatus() != FinancialDocumentStatus.DRAFT)) {
            return "FINALIZED";
        }
        return "DRAFT";
    }

    private Instant resolveEncounterInstant(PatientEncounter encounter) {
        LocalDate date = encounter.getEncounterDate();
        LocalTime time = encounter.getEncounterTime();
        if (date != null && time != null) {
            return date.atTime(time).atZone(DISPLAY_ZONE).toInstant();
        }
        if (date != null) {
            return date.atStartOfDay(DISPLAY_ZONE).toInstant();
        }
        return encounter.getCreatedDate();
    }

    private String fullName(Patient patient) {
        return String.join(
                " ",
                List.of(
                        Objects.toString(patient.getFirstName(), "").trim(),
                        Objects.toString(patient.getSecondName(), "").trim(),
                        Objects.toString(patient.getThirdName(), "").trim(),
                        Objects.toString(patient.getLastName(), "").trim()
                ).stream().filter(this::hasText).toList()
        ).trim();
    }

    private String resolveCurrency(List<BillingCharge> charges) {
        return charges.stream()
                .map(BillingCharge::getCurrency)
                .filter(Objects::nonNull)
                .findFirst()
                .map(Currency::name)
                .orElse("SAR");
    }

    private String resolveCurrency(BillingCharge charge, BillingWallet wallet) {
        if (charge != null && charge.getCurrency() != null) {
            return charge.getCurrency().name();
        }
        if (wallet != null && wallet.getCurrency() != null) {
            return wallet.getCurrency().name();
        }
        return "SAR";
    }

    private boolean isSubmitted(ClaimStatus status) {
        return status == ClaimStatus.SUBMITTING
                || status == ClaimStatus.SUBMITTED
                || status == ClaimStatus.ACCEPTED
                || status == ClaimStatus.REJECTED;
    }

    private String formatParty(PayerType payerType) {
        return switch (payerType) {
            case PATIENT -> "Patient";
            case INSURANCE -> "Insurance";
            default -> payerType.name();
        };
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BigDecimal toDecimal(Number value) {
        return value == null
                ? zero()
                : new BigDecimal(value.toString()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? zero()
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record PartyTotals(
            BigDecimal total,
            BigDecimal allocated,
            BigDecimal outstanding
    ) {
    }

    private record ComponentTotals(
            BigDecimal deductible,
            BigDecimal copay,
            BigDecimal nonCovered
    ) {
    }
}
