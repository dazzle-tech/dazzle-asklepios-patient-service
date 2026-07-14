package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientCharge;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientLedgerEntry;
import com.dazzle.asklepios.domain.PatientPaymentAllocation;
import com.dazzle.asklepios.domain.PatientPayments;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PatientWallet;
import com.dazzle.asklepios.domain.WalletTransaction;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.LedgerAccount;
import com.dazzle.asklepios.domain.enumeration.LedgerEntryType;
import com.dazzle.asklepios.domain.enumeration.LedgerSource;
import com.dazzle.asklepios.domain.enumeration.PaymentLifecycleStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.WalletTransactionType;
import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientChargeRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientLedgerRepository;
import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;
import com.dazzle.asklepios.repository.PatientPaymentsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PatientWalletRepository;
import com.dazzle.asklepios.repository.WalletTransactionRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.dto.patientPayments.PatientLedgerSummaryDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentCreateDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentDetailsDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentFormDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentServiceItemDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PaymentAllocationDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPaymentsService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPaymentsService.class);

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

    private final FinancialDocumentItemRepository itemRepo;

    private final PatientPaymentsRepository paymentRepository;
    private final PatientServiceAndProductRepository serviceRepository;

    private final PatientRepository patientRepository;
    private final PatientEncounterRepository encounterRepository;
    private final PatientInsuranceRepository insuranceRepository;

    private final PatientWalletRepository walletRepository;
    private final PatientChargeRepository chargeRepository;
    private final PatientPaymentAllocationRepository allocationRepository;

    private final PatientLedgerRepository ledgerRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final InsuranceCalculationService insuranceCalculationService;
    private final WaseelEligibilityRequestRepository waseelEligibilityRequestRepository;
    private final FinancialDocumentStatusService documentStatusService;
    private final FinancialDocumentRepository documentRepository;

    private final WaseelCoverageExtractionService coverageExtractionService;

    private static BigDecimal nonNullAmount(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value;
    }

    private PatientPaymentFormDTO toFormDTO(
            PatientPayments payment,
            List<PatientServiceAndProduct> services
    ) {
        return new PatientPaymentFormDTO(
                payment.getId(),
                payment.getPatient() != null ? payment.getPatient().getId() : null,
                payment.getEncounter() != null ? payment.getEncounter().getId() : null,
                payment.getPlan() != null ? payment.getPlan().getId() : null,

                payment.getPaymentTypes(),
                payment.getPaymentMethods(),

                payment.getAmount(),
                payment.getCurrency(),
                payment.getFacilityDefaultCurrency(),
                null,
                payment.getAmountInFacilityCurrency(),

                nonNullAmount(payment.getDueAmount()),
                nonNullAmount(payment.getPatientBalance()),
                nonNullAmount(payment.getRemaining()),
                nonNullAmount(payment.getRefunds()),
                nonNullAmount(payment.getPaidFromAmount()),
                nonNullAmount(payment.getPaidFromBalance()),

                payment.getAddToFreeBalance(),
                payment.getUseBalanceToSettleDebts(),

                payment.getCardNumber(),
                payment.getCardHolderName(),
                payment.getCardValidUntil(),

                payment.getChequeNumber(),
                payment.getChequeBankName(),
                payment.getChequeDueDate(),

                payment.getTransferNumber(),
                payment.getTransferBankName(),
                payment.getTransferDate(),

                services
        );
    }

    private BigDecimal getPaidAmountInFacilityCurrency(PatientPayments payment) {
        if (payment.getAmountInFacilityCurrency() != null) {
            return payment.getAmountInFacilityCurrency();
        }
        return nonNullAmount(payment.getAmount());
    }

//    private BigDecimal computeDueFromServices(Long paymentId) {
//        List<PatientServiceAndProduct> services = serviceRepository.findByPaymentId(paymentId);
//
//        return services.stream()
//                .filter(serviceRow -> !Boolean.TRUE.equals(serviceRow.getIsExempted()))
//                .map(this::resolveServiceAmount)
//                .reduce(ZERO_AMOUNT, BigDecimal::add);
//    }

    private BigDecimal resolveServiceAmount(PatientServiceAndProduct serviceRow) {
        if (serviceRow == null) {
            return ZERO_AMOUNT;
        }

        if (serviceRow.getNetAmount() != null && serviceRow.getNetAmount().compareTo(ZERO_AMOUNT) > 0) {
            return serviceRow.getNetAmount();
        }

        if (serviceRow.getTotalAmount() != null && serviceRow.getTotalAmount().compareTo(ZERO_AMOUNT) > 0) {
            return serviceRow.getTotalAmount();
        }

        if (serviceRow.getUnitPrice() != null) {
            BigDecimal quantity = BigDecimal.valueOf(
                    serviceRow.getQuantity() == null ? 1L : serviceRow.getQuantity()
            );
            return serviceRow.getUnitPrice().multiply(quantity);
        }

        return ZERO_AMOUNT;
    }

    private BigDecimal resolveServiceAmountFromDTO(PatientPaymentServiceItemDTO item) {

        BigDecimal unitPrice = nonNullAmount(item.price());
        BigDecimal quantity = BigDecimal.ONE;

        BigDecimal factor = BigDecimal.ONE; // لاحقًا من discount
        BigDecimal tax = ZERO_AMOUNT;       // لاحقًا من tax

        return unitPrice.multiply(quantity).multiply(factor).add(tax);
    }

    private void lockPatientLedger(Long patientId) {
        jdbcTemplate.execute(
                "select pg_advisory_xact_lock(?)",
                (PreparedStatementCallback<Void>) ps -> {
                    ps.setLong(1, patientId);
                    ps.execute();
                    return null;
                }
        );
    }

    private PatientWallet getOrCreateWalletLocked(Long patientId) {
        return walletRepository.findByPatientId(patientId).orElseGet(() -> {
            PatientWallet newWallet = PatientWallet.builder()
                    .patientId(patientId)
                    .balance(ZERO_AMOUNT)
                    .lastModifiedDate(Instant.now())
                    .build();

            try {
                return walletRepository.saveAndFlush(newWallet);
            } catch (DataIntegrityViolationException conflict) {
                LOG.warn("[WALLET] create conflict patientId={} - reloading", patientId);
                return walletRepository.findByPatientId(patientId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Wallet row is missing after conflict for patient " + patientId
                        ));
            }
        });
    }

    private PatientCharge getOrCreateChargeForEncounterLocked(
            PatientPayments payment,
            BigDecimal dueAmount
    ) {

        Long encounterId = payment.getEncounter().getId();

        // ✅ check existing
        PatientCharge existing = chargeRepository.findByEncounterId(encounterId).orElse(null);
        if (existing != null) {
            return existing;
        }

        // ✅ build new charge
        PatientCharge newCharge = PatientCharge.builder()
                .patientId(payment.getPatient().getId())
                .encounterId(encounterId)
                .planId(payment.getPlan() != null ? payment.getPlan().getId() : null)
                .dueAmount(nonNullAmount(dueAmount))
                .remaining(nonNullAmount(dueAmount))
                .currency(payment.getCurrency())
                .facilityDefaultCurrency(payment.getFacilityDefaultCurrency().name())
                .createdDate(Instant.now())
                .lastModifiedDate(Instant.now())
                .build();

        try {
            // ✅ save charge
            chargeRepository.saveAndFlush(newCharge);

            LOG.debug(
                    "[CHARGE] created encounterId={} patientId={} due={}",
                    encounterId,
                    newCharge.getPatientId(),
                    newCharge.getDueAmount()
            );

            // ✅ Ledger (Double Entry)

// Debit → Patient owes money
            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(newCharge.getPatientId())
                            .type(LedgerEntryType.DEBIT)
                            .account(LedgerAccount.PATIENT_RECEIVABLE)
                            .source(LedgerSource.CHARGE)
                            .referenceId(newCharge.getId())
                            .amount(newCharge.getDueAmount())
                            .currency(newCharge.getCurrency())
                            .createdDate(Instant.now())
                            .build()
            );

// Credit → Revenue
            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(newCharge.getPatientId())
                            .type(LedgerEntryType.CREDIT)
                            .account(LedgerAccount.REVENUE)
                            .source(LedgerSource.CHARGE)
                            .referenceId(newCharge.getId())
                            .amount(newCharge.getDueAmount())
                            .currency(newCharge.getCurrency())
                            .createdDate(Instant.now())
                            .build()
            );


            return newCharge;

        } catch (DataIntegrityViolationException conflict) {

            // ✅ race condition protection
            LOG.warn("[CHARGE] conflict encounterId={} → reloading", encounterId);

            return chargeRepository.findByEncounterId(encounterId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Charge row missing after conflict for encounter " + encounterId
                    ));
        }
    }
    private void updateDocumentStatus(Long encounterId) {

        FinancialDocument document =
                documentRepository.findByEncounterId(encounterId)
                        .orElseThrow(() -> new IllegalStateException("Document not found"));

        FinancialDocumentStatus status =
                documentStatusService.calculate(document.getId());

        document.setStatus(status);

        documentRepository.save(document);
    }

    public PatientPaymentDetailsDTO postPayment(Long paymentId) {

        PatientPayments payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Payment not found",
                        "patientPayments",
                        "notfound"
                ));

        // ✅ ✅ validate amount
        if (payment.getAmount() == null
                || payment.getAmount().compareTo(ZERO_AMOUNT) <= 0) {

            throw new BadRequestAlertException(
                    "Invalid payment amount",
                    "patientPayments",
                    "invalid.amount"
            );
        }

        // ✅ prevent double execution
        if (!PaymentLifecycleStatus.CREATED.equals(payment.getStatus())) {
            throw new BadRequestAlertException(
                    "Payment already posted",
                    "patientPayments",
                    "already.posted"
            );
        }

        LOG.info("[POST] Executing paymentId={}", paymentId);

        try {

            // ✅ ✅ HYBRID switch
            if (hasManualAllocations(paymentId)) {

                LOG.info("[POST] Using MANUAL allocation flow paymentId={}", paymentId);

                applyManualAllocationFlow(payment);

            } else {

                LOG.info("[POST] Using LEGACY charge allocation flow paymentId={}", paymentId);

                applyLedgerForPayment(paymentId);
            }

            // ✅ ✅ Calculate Payment Status
            PaymentStatus paymentStatus = calculatePaymentStatus(paymentId);
            payment.setPaymentStatus(paymentStatus);

            LOG.info("[POST] paymentId={} paymentStatus={}", paymentId, paymentStatus);
            FinancialDocument document =
                    documentRepository.findById(payment.getDocumentId())
                            .orElseThrow(() -> new IllegalStateException("Document not found"));


            FinancialDocumentStatus status =
                    documentStatusService.calculate(document.getId());

            document.setStatus(status);

            documentRepository.save(document);
        } catch (Exception ex) {

            LOG.error("[POST] failed paymentId={}", paymentId, ex);
            throw ex;
        }

        // ✅ update lifecycle status
        payment.setStatus(PaymentLifecycleStatus.POSTED);

        LOG.info("[POST] paymentId={} lifecycleStatus={} → POSTED",
                paymentId,
                payment.getStatus());

        paymentRepository.save(payment);

        return finalizeAndReturnDetails(payment);
    }
    private boolean hasManualAllocations(Long paymentId) {
        return allocationRepository.existsByPaymentIdAndDocumentItemIdIsNotNull(paymentId);
    }
    private void applyManualAllocationFlow(PatientPayments payment)
    {

        List<PatientPaymentAllocation> allocations =
                allocationRepository.findByPaymentId(payment.getId());

        for (PatientPaymentAllocation alloc : allocations) {

            if (alloc.getDocumentItemId() == null) continue;

            FinancialDocumentItem item =
                    itemRepo.findById(alloc.getDocumentItemId())
                            .orElseThrow(() -> new IllegalStateException("Item not found"));

            BigDecimal currentPaid = nonNullAmount(alloc.getPaidFromAmount());
            if (currentPaid.compareTo(ZERO_AMOUNT) <= 0) {
                continue;
            }

            // ✅ update ledger per item
            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(payment.getPatient().getId())
                            .type(LedgerEntryType.DEBIT)
                            .account(LedgerAccount.CASH)
                            .source(LedgerSource.PAYMENT)
                            .referenceId(payment.getId())
                            .amount(currentPaid)
                            .currency(payment.getCurrency())// أو من payment
                            .createdDate(Instant.now())
                            .build()
            );

            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(payment.getPatient().getId())
                            .type(LedgerEntryType.CREDIT)
                            .account(LedgerAccount.PATIENT_RECEIVABLE)
                            .source(LedgerSource.PAYMENT)
                            .referenceId(payment.getId())
                            .amount(currentPaid)
                            .currency(payment.getCurrency())
                            .createdDate(Instant.now())
                            .build()
            );
        }
    }
    private void applyLedgerForPayment(Long paymentId) {

        PatientPayments payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Payment not found with id " + paymentId,
                        "patientPayments",
                        "payment.notfound"
                ));

        Long patientId = payment.getPatient().getId();
        Long encounterId = payment.getEncounter().getId();

        lockPatientLedger(patientId);

        PatientWallet wallet = getOrCreateWalletLocked(patientId);
        BigDecimal walletBalance = nonNullAmount(wallet.getBalance());

// ✅ correct validation
        if ((payment.getAmount() == null || payment.getAmount().compareTo(ZERO_AMOUNT) <= 0)
                && walletBalance.compareTo(ZERO_AMOUNT) <= 0) {

            throw new BadRequestAlertException(
                    "No payment or wallet balance available",
                    "patientPayments",
                    "invalid.amount"
            );
        }

        BigDecimal dueAmount = nonNullAmount(payment.getDueAmount());

// ✅ Coverage initialization
        InsuranceCoverage coverage;

// ✅ Only load eligibility for INSURANCE payments
        if (payment.getPaymentTypes() == PaymentTypes.INSURANCE_PLAN) {

            WaseelEligibilityRequest eligibility =
                    waseelEligibilityRequestRepository
                            .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                    patientId,
                                    "SUCCESS"
                            )
                            .orElseThrow(() -> new IllegalStateException("No valid eligibility found"));

            coverage = coverageExtractionService.extractCoverage(
                    eligibility.getResponseJson()
            );

            // ✅ Null validation
            if (coverage.getCopaymentPercent() == null ||
                    coverage.getCopaymentCap() == null) {

                throw new IllegalStateException("Invalid coverage data from Waseel");
            }

            // ✅ Logical validation
            if (coverage.getCopaymentPercent().compareTo(ZERO_AMOUNT) == 0
                    && coverage.getCopaymentCap().compareTo(ZERO_AMOUNT) == 0) {

                throw new IllegalStateException("Invalid coverage (both percent and cap are zero)");
            }

        } else {
            // ✅ CASH → no insurance involvement
            coverage = new InsuranceCoverage(ZERO_AMOUNT, ZERO_AMOUNT);
        }

// ✅ Continue normal flow
        PatientCharge currentEncounterCharge =
                getOrCreateChargeForEncounterLocked(payment, dueAmount);

        List<PatientCharge> openCharges = chargeRepository
                .findByPatientIdAndRemainingGreaterThanOrderByCreatedDateAscIdAsc(
                        patientId,
                        ZERO_AMOUNT
                );

        openCharges.sort(Comparator
                .comparing((PatientCharge c) -> !c.getEncounterId().equals(encounterId))
                .thenComparing(PatientCharge::getCreatedDate)
                .thenComparing(PatientCharge::getId));

        BigDecimal paymentAmount = nonNullAmount(getPaidAmountInFacilityCurrency(payment));
        BigDecimal remainingPaymentAmount = paymentAmount;

        BigDecimal paidFromPaymentAmount = ZERO_AMOUNT;
        BigDecimal paidFromWalletBalance = ZERO_AMOUNT;

        List<PatientServiceAndProduct> services =
                serviceRepository.findByPaymentId(paymentId);

        BigDecimal totalPatientShare = ZERO_AMOUNT;
        BigDecimal totalInsuranceShareFromServices = ZERO_AMOUNT;

        for (PatientServiceAndProduct service : services) {

            BigDecimal net = resolveServiceAmount(service);

            // ✅ calculation per service
            BigDecimal servicePatientShare =
                    net.multiply(coverage.getCopaymentPercent())
                            .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            servicePatientShare =
                    servicePatientShare.min(coverage.getCopaymentCap());

            BigDecimal serviceInsuranceShare =
                    net.subtract(servicePatientShare);

            // ✅ update entity
            service.setPatientShareAmount(servicePatientShare);
            service.setInsuranceShareAmount(serviceInsuranceShare);
            service.setPaidAmount(ZERO_AMOUNT);
            service.setRemainingAmount(servicePatientShare);
            totalPatientShare = totalPatientShare.add(servicePatientShare);
            totalInsuranceShareFromServices =
                    totalInsuranceShareFromServices.add(serviceInsuranceShare);
        }


        serviceRepository.saveAll(services);
// ✅ IMPORTANT: update charge to reflect ONLY patient debt
        BigDecimal patientShare = totalPatientShare;

        if (payment.getPaymentTypes() == PaymentTypes.INSURANCE_PLAN) {

            currentEncounterCharge.setRemaining(totalPatientShare);
            currentEncounterCharge.setDueAmount(totalPatientShare);

        } else {

            // ✅ CASH → full due
            currentEncounterCharge.setRemaining(dueAmount);
            currentEncounterCharge.setDueAmount(dueAmount);
        }

        BigDecimal totalInsuranceShare = totalInsuranceShareFromServices;

        BigDecimal remainingInsuranceShare = totalInsuranceShare;

        if (totalPatientShare.add(totalInsuranceShare).compareTo(dueAmount) != 0) {
            throw new IllegalStateException("Split mismatch with total amount");
        }

        if (payment.getPaymentTypes() == PaymentTypes.INSURANCE_PLAN) {

            if (payment.getAmount() == null ||
                    payment.getAmount().compareTo(ZERO_AMOUNT) <= 0) {

                throw new IllegalStateException(
                        "Payment must be greater than zero"
                );
            }

            // ✅ allow partial but prevent overpayment
            if (payment.getAmount().compareTo(patientShare) > 0) {

                throw new IllegalStateException(
                        "Payment exceeds patient share"
                );
            }
        }

        // ==============================
        // ✅ Allocation Loop
        // ==============================
        for (PatientCharge openCharge : openCharges) {

            // ✅ stop if no more payment
            if (remainingPaymentAmount.signum() <= 0) break;

            // ✅ skip if already settled
            if (nonNullAmount(openCharge.getRemaining()).signum() <= 0) continue;

            // ✅ ===============================
            // ✅ WALLET FIRST (ONLY SAME VISIT)
            // ✅ ===============================
            boolean isCurrentEncounter =
                    openCharge.getEncounterId().equals(encounterId);

            if (isCurrentEncounter &&
                    walletBalance.compareTo(ZERO_AMOUNT) > 0 &&
                    openCharge.getRemaining().compareTo(ZERO_AMOUNT) > 0) {

                BigDecimal walletPortion =
                        walletBalance.min(openCharge.getRemaining());

                openCharge.setRemaining(
                        openCharge.getRemaining().subtract(walletPortion)
                );

                walletBalance = walletBalance.subtract(walletPortion);

                PatientPaymentAllocation allocation =
                        allocationRepository.findByPaymentIdAndChargeId(paymentId, openCharge.getId())
                                .orElseGet(() -> PatientPaymentAllocation.builder()
                                        .paymentId(paymentId)
                                        .chargeId(openCharge.getId())
                                        .paidFromAmount(ZERO_AMOUNT)
                                        .paidFromBalance(ZERO_AMOUNT)
                                        .lastModifiedDate(Instant.now())
                                        .build());

                allocation.setPaidFromBalance(
                        nonNullAmount(allocation.getPaidFromBalance()).add(walletPortion)
                );

                allocationRepository.save(allocation);

                paidFromWalletBalance = paidFromWalletBalance.add(walletPortion);

                // ✅ ledger (wallet usage)
                ledgerRepository.save(
                        PatientLedgerEntry.builder()
                                .patientId(patientId)
                                .type(LedgerEntryType.DEBIT)
                                .account(LedgerAccount.LIABILITY)
                                .source(LedgerSource.WALLET)
                                .referenceId(paymentId)
                                .amount(walletPortion)
                                .currency(payment.getCurrency())
                                .createdDate(Instant.now())
                                .build()
                );

                ledgerRepository.save(
                        PatientLedgerEntry.builder()
                                .patientId(patientId)
                                .type(LedgerEntryType.CREDIT)
                                .account(LedgerAccount.PATIENT_RECEIVABLE)
                                .source(LedgerSource.WALLET)
                                .referenceId(paymentId)
                                .amount(walletPortion)
                                .currency(payment.getCurrency())
                                .createdDate(Instant.now())
                                .build()
                );
            }

            // ✅ ===============================
            // ✅ CASH ALLOCATION
            // ✅ ===============================
            if (remainingPaymentAmount.signum() <= 0) break;

            BigDecimal amountToPay =
                    remainingPaymentAmount.min(openCharge.getRemaining());

            BigDecimal patientPortion;

            if (payment.getPaymentTypes() == PaymentTypes.INSURANCE_PLAN) {
                patientPortion = amountToPay.min(patientShare);
            } else {
                // ✅ CASH = full amount goes to patient
                patientPortion = amountToPay;
            }

            BigDecimal insurancePortion =
                    amountToPay.subtract(patientPortion)
                            .min(remainingInsuranceShare);

            // ✅ update trackers
            patientShare = patientShare.subtract(patientPortion);
            remainingInsuranceShare = remainingInsuranceShare.subtract(insurancePortion);
            remainingPaymentAmount = remainingPaymentAmount.subtract(amountToPay);

            // ✅ update charge
            openCharge.setRemaining(
                    openCharge.getRemaining().subtract(amountToPay)
            );
            openCharge.setLastModifiedDate(Instant.now());

            PatientPaymentAllocation allocation =
                    allocationRepository.findByPaymentIdAndChargeId(paymentId, openCharge.getId())
                            .orElseGet(() -> PatientPaymentAllocation.builder()
                                    .paymentId(paymentId)
                                    .chargeId(openCharge.getId())
                                    .paidFromAmount(ZERO_AMOUNT)
                                    .paidFromBalance(ZERO_AMOUNT)
                                    .lastModifiedDate(Instant.now())
                                    .build());

            allocation.setPaidFromAmount(
                    nonNullAmount(allocation.getPaidFromAmount()).add(patientPortion)
            );

            allocationRepository.save(allocation);

            paidFromPaymentAmount =
                    paidFromPaymentAmount.add(patientPortion);

            // ✅ ===============================
            // ✅ ledger CASH
            // ✅ ===============================
            if (patientPortion.compareTo(ZERO_AMOUNT) > 0) {

                ledgerRepository.save(
                        PatientLedgerEntry.builder()
                                .patientId(patientId)
                                .type(LedgerEntryType.DEBIT)
                                .account(LedgerAccount.CASH)
                                .source(LedgerSource.PAYMENT)
                                .referenceId(paymentId)
                                .amount(patientPortion)
                                .currency(payment.getCurrency())
                                .createdDate(Instant.now())
                                .build()
                );

                ledgerRepository.save(
                        PatientLedgerEntry.builder()
                                .patientId(patientId)
                                .type(LedgerEntryType.CREDIT)
                                .account(LedgerAccount.PATIENT_RECEIVABLE)
                                .source(LedgerSource.PAYMENT)
                                .referenceId(paymentId)
                                .amount(patientPortion)
                                .currency(payment.getCurrency())
                                .createdDate(Instant.now())
                                .build()
                );
            }
        }
        for (PatientServiceAndProduct service : services) {

            BigDecimal total = nonNullAmount(service.getPatientShareAmount());

            BigDecimal paid = allocationRepository
                    .sumPaidForCharge(currentEncounterCharge.getId()); // تحتاجي method

            BigDecimal remaining = total.subtract(nonNullAmount(paid));

            service.setPaidAmount(nonNullAmount(paid));
            service.setRemainingAmount(remaining.max(ZERO_AMOUNT));
        }
        // ==============================
        // ✅ Insurance Ledger (FIXED)
        // ==============================
        if (payment.getPaymentTypes() == PaymentTypes.INSURANCE_PLAN &&
                totalInsuranceShare.compareTo(ZERO_AMOUNT) > 0) {

            // ✅ Debit → Insurance receivable
            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(patientId)
                            .type(LedgerEntryType.DEBIT)
                            .account(LedgerAccount.INSURANCE_RECEIVABLE)
                            .source(LedgerSource.PAYMENT)
                            .referenceId(paymentId)
                            .amount(totalInsuranceShare)
                            .currency(payment.getCurrency())
                            .createdDate(Instant.now())
                            .build()
            );

            // ✅ Credit → reduce receivable
            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(patientId)
                            .type(LedgerEntryType.CREDIT)
                            .account(LedgerAccount.PATIENT_RECEIVABLE)
                            .source(LedgerSource.PAYMENT)
                            .referenceId(paymentId)
                            .amount(totalInsuranceShare)
                            .currency(payment.getCurrency())
                            .createdDate(Instant.now())
                            .build()
            );
        }

        // ==============================
        // ✅ Leftover → Wallet
        // ==============================
        BigDecimal refunds = ZERO_AMOUNT;

        if (remainingPaymentAmount.signum() > 0) {

            if (Boolean.TRUE.equals(payment.getAddToFreeBalance())) {

                walletBalance = walletBalance.add(remainingPaymentAmount);

                walletTransactionRepository.save(
                        WalletTransaction.builder()
                                .patientId(patientId)
                                .type(WalletTransactionType.DEPOSIT)
                                .amount(remainingPaymentAmount)
                                .referenceId(paymentId)
                                .createdDate(Instant.now())
                                .build()
                );

                ledgerRepository.save(
                        PatientLedgerEntry.builder()
                                .patientId(patientId)
                                .type(LedgerEntryType.DEBIT)
                                .account(LedgerAccount.CASH)
                                .source(LedgerSource.WALLET)
                                .referenceId(paymentId)
                                .amount(remainingPaymentAmount)
                                .currency(payment.getCurrency())
                                .createdDate(Instant.now())
                                .build()
                );

                ledgerRepository.save(
                        PatientLedgerEntry.builder()
                                .patientId(patientId)
                                .type(LedgerEntryType.CREDIT)
                                .account(LedgerAccount.LIABILITY)
                                .source(LedgerSource.WALLET)
                                .referenceId(paymentId)
                                .amount(remainingPaymentAmount)
                                .currency(payment.getCurrency())
                                .createdDate(Instant.now())
                                .build()
                );
            } else {
                refunds = remainingPaymentAmount;
            }

            remainingPaymentAmount = ZERO_AMOUNT;
        }

        // ==============================
        // ✅ Save updates
        // ==============================
        chargeRepository.saveAll(openCharges);

        wallet.setBalance(walletBalance.max(ZERO_AMOUNT));
        wallet.setLastModifiedDate(Instant.now()); // ✅ FIX
        walletRepository.save(wallet);

        payment.setRemaining(remainingPaymentAmount);
        payment.setPaidFromAmount(paidFromPaymentAmount);
        payment.setPaidFromBalance(paidFromWalletBalance);
        payment.setRefunds(refunds);
        payment.setPatientBalance(wallet.getBalance());

        // ==============================
        // ✅ Balance Validation
        // ==============================
        BigDecimal totalDebit = ledgerRepository.sumDebitByPatient(patientId);
        BigDecimal totalCredit = ledgerRepository.sumCreditByPatient(patientId);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException(
                    "Ledger NOT BALANCED for patientId=" + patientId +
                            " debit=" + totalDebit +
                            " credit=" + totalCredit
            );
        }

        paymentRepository.save(payment);
    }


    private void syncPaymentServicesAfterLedger(Long paymentId) {
        List<PatientServiceAndProduct> services = serviceRepository.findByPaymentId(paymentId);

        for (PatientServiceAndProduct service : services) {
            BigDecimal netAmount = resolveServiceAmount(service);

            service.setPaidAmount(ZERO_AMOUNT);
            service.setRemainingAmount(netAmount);

            if (Boolean.TRUE.equals(service.getIsExempted())) {
                service.setPaymentStatus(PaymentStatus.PAID);
                service.setPaidAmount(ZERO_AMOUNT);
                service.setRemainingAmount(ZERO_AMOUNT);
                continue;
            }

            if (netAmount.compareTo(ZERO_AMOUNT) == 0) {
                service.setPaymentStatus(PaymentStatus.PAID);
                service.setPaidAmount(ZERO_AMOUNT);
                service.setRemainingAmount(ZERO_AMOUNT);
            } else {
                service.setPaymentStatus(PaymentStatus.PENDING);
                service.setRemainingAmount(netAmount);
            }
        }

        serviceRepository.saveAll(services);
    }

    private PatientPaymentDetailsDTO finalizeAndReturnDetails(PatientPayments saved) {

        entityManager.flush();
        entityManager.clear();

        PatientPayments refreshed = entityManager.find(PatientPayments.class, saved.getId());

        List<PatientServiceAndProduct> refreshedServices =
                serviceRepository.findByPaymentId(refreshed.getId());

        BigDecimal amountPaid = refreshed.getAmountInFacilityCurrency() != null
                ? refreshed.getAmountInFacilityCurrency()
                : nonNullAmount(refreshed.getAmount());

        return new PatientPaymentDetailsDTO(
                refreshed.getId(),
                refreshed.getPatient() != null ? refreshed.getPatient().getId() : null,
                refreshed.getEncounter() != null ? refreshed.getEncounter().getId() : null,
                nonNullAmount(refreshed.getDueAmount()),
                nonNullAmount(refreshed.getPatientBalance()),
                amountPaid,
                nonNullAmount(refreshed.getRemaining()),
                nonNullAmount(refreshed.getRefunds()),
                nonNullAmount(refreshed.getPaidFromAmount()),
                nonNullAmount(refreshed.getPaidFromBalance()),
                refreshedServices
        );
    }


    public PatientPaymentDetailsDTO create(PatientPaymentCreateDTO dto) {
        LOG.info("[CREATE] PatientPayments payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] PatientPayments rejected: patient not found patientId={}", dto.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "patientPayments",
                            "patient.notfound"
                    );
                });

        PatientEncounter encounter = encounterRepository.findById(dto.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] PatientPayments rejected: encounter not found encounterId={}", dto.encounterId());
                    return new NotFoundAlertException(
                            "Encounter not found with id " + dto.encounterId(),
                            "patientPayments",
                            "encounter.notfound"
                    );
                });

        if (encounter.getStatus() != EncounterStatus.PENDING_PAYMENT) {
            LOG.warn(
                    "[CREATE] PatientPayments rejected: encounter status invalid encounterId={} status={}",
                    dto.encounterId(),
                    encounter.getStatus()
            );
            throw new BadRequestAlertException(
                    "Encounter is not in PENDING_PAYMENT status",
                    "patientPayments",
                    "encounter.invalid.status"
            );
        }


        List<PatientPaymentServiceItemDTO> services =
                dto.services() == null ? List.of() : dto.services();

        BigDecimal dueAmount = services.stream()
                .filter(serviceItem -> !Boolean.TRUE.equals(serviceItem.isExempted()))
                .map(this::resolveServiceAmountFromDTO)
                .reduce(ZERO_AMOUNT, BigDecimal::add);

        if (services.isEmpty() || dueAmount.compareTo(ZERO_AMOUNT) == 0) {
            LOG.info(
                    "[CREATE] Skipping payment step for encounterId={} because there are no services to pay",
                    dto.encounterId()
            );

            if (encounter.getEncounterType().equals(EncounterType.EMERGENCY)) {
                encounter.setStatus(EncounterStatus.WAITING_TRIAGE);
            } else {
                encounter.setStatus(EncounterStatus.NEW);
            }

            encounterRepository.saveAndFlush(encounter);

            List<PatientServiceAndProduct> skippedServiceRows =
                    buildSkippedServiceRows(encounter.getId(), services);

            return new PatientPaymentDetailsDTO(
                    null,
                    patient.getId(),
                    encounter.getId(),
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    skippedServiceRows
            );
        }

        PatientInsurance plan = resolvePlan(dto);

        PatientPayments payment = PatientPayments.builder()
                .patient(patient)
                .encounter(encounter)
                .plan(plan)
                .dueAmount(dueAmount)
                .patientBalance(ZERO_AMOUNT)
                .paidFromAmount(ZERO_AMOUNT)
                .paidFromBalance(ZERO_AMOUNT)
                .refunds(ZERO_AMOUNT)
                .paymentTypes(dto.paymentTypes())
                .paymentMethods(dto.paymentMethods())
                .amount(dto.amount())
                .currency(dto.currency())
                .facilityDefaultCurrency(dto.facilityDefaultCurrency())
                .amountInFacilityCurrency(dto.amountInFacilityCurrency())
                .remaining(ZERO_AMOUNT)
                .addToFreeBalance(dto.addToFreeBalance())
                .useBalanceToSettleDebts(dto.useBalanceToSettleDebts())
                .cardNumber(dto.cardNumber())
                .cardHolderName(dto.cardHolderName())
                .cardValidUntil(dto.cardValidUntil())
                .chequeNumber(dto.chequeNumber())
                .chequeBankName(dto.chequeBankName())
                .chequeDueDate(dto.chequeDueDate())
                .transferNumber(dto.transferNumber())
                .transferBankName(dto.transferBankName())
                .transferDate(dto.transferDate())
                .status(PaymentLifecycleStatus.CREATED)
                .build();

        try {
            PatientPayments saved = paymentRepository.saveAndFlush(payment);

            // ==============================
// ✅ CREATE OR LOAD MASTER INVOICE 💣
// ==============================

            FinancialDocument document =
                    documentRepository.findByEncounterIdAndDocumentType(
                            encounter.getId(),
                            FinancialDocumentType.INVOICE
                    ).orElseGet(() -> {

                        // ✅ enforce one invoice
                        if (documentRepository.existsByEncounterIdAndDocumentType(
                                encounter.getId(),
                                FinancialDocumentType.INVOICE)) {

                            throw new IllegalStateException("Invoice already exists for this encounter");
                        }

                        // ✅ create new invoice
                        FinancialDocument newInvoice = FinancialDocument.builder()
                                .documentType(FinancialDocumentType.INVOICE)
                                .encounterId(encounter.getId())
                                .patientId(patient.getId())
                                .status(FinancialDocumentStatus.ISSUED)
                                .totalAmount(ZERO_AMOUNT)
                                .currency(dto.currency())
                                .createdDate(Instant.now())
                                .build();

                        return documentRepository.save(newInvoice);
                    });

// ✅ link payment to document
            saved.setDocumentId(document.getId());

// ✅ save again
            paymentRepository.save(saved);
            // ✅ attach services FIRST (always needed)
            List<PatientServiceAndProduct> serviceRows =
                    attachServicesToPayment(saved, services);

            LOG.info(
                    "[CREATE] PatientPayments saved paymentId={} patientId={} encounterId={} servicesCount={} dueAmount={}",
                    saved.getId(),
                    dto.patientId(),
                    dto.encounterId(),
                    serviceRows.size(),
                    dueAmount
            );
// ==============================
// ✅ UPDATE INVOICE TOTAL 💣
// ==============================

            BigDecimal totalInvoiceAmount = serviceRows.stream()
                    .map(s -> nonNullAmount(s.getNetAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            document.setTotalAmount(totalInvoiceAmount);

            documentRepository.save(document);

            LOG.info("[CREATE] invoiceId={} totalAmount={}",
                    document.getId(),
                    totalInvoiceAmount);

            // ✅ update encounter status
            if (encounter.getEncounterType().equals(EncounterType.EMERGENCY)) {
                encounter.setStatus(EncounterStatus.WAITING_TRIAGE);
            } else {
                encounter.setStatus(EncounterStatus.NEW);
            }

            encounterRepository.saveAndFlush(encounter);

            // ✅ AUTO POST only for CASH
            if (dto.paymentTypes() == PaymentTypes.CASH ||
                    dto.paymentTypes() == PaymentTypes.INSURANCE_PLAN) {

                return postPayment(saved.getId());
            }

            // ✅ Insurance / others → no post
            return buildInitialResponse(saved);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] PatientPayments failed (constraint) payload={}", dto, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[CREATE] PatientPayments failed (unexpected) payload={}", dto, ex);
            throw ex;
        }
    }


    private PatientPaymentDetailsDTO buildInitialResponse(PatientPayments payment) {
        return new PatientPaymentDetailsDTO(
                payment.getId(),
                payment.getPatient().getId(),
                payment.getEncounter().getId(),
                payment.getDueAmount(),
                ZERO_AMOUNT, // patient balance (not updated yet)
                payment.getAmount(),
                ZERO_AMOUNT,
                ZERO_AMOUNT,
                ZERO_AMOUNT,
                ZERO_AMOUNT,
                List.of()
        );
    }
    public void allocatePaymentManually(
            Long paymentId,
            List<PaymentAllocationDTO> allocations
    ) {

        for (PaymentAllocationDTO dto : allocations) {

            FinancialDocumentItem item = itemRepo.findById(dto.documentItemId())
                    .orElseThrow(() -> new IllegalStateException("Item not found"));

            BigDecimal amount = nonNullAmount(dto.amount());

            BigDecimal alreadyPaid =
                    allocationRepository.sumPaidForItem(item.getId());

            BigDecimal remaining =
                    item.getNetAmount().subtract(alreadyPaid);

            if (amount.compareTo(remaining) > 0) {
                throw new IllegalStateException("Exceeds remaining");
            }

            allocationRepository.save(
                    PatientPaymentAllocation.builder()
                            .paymentId(paymentId)
                            .documentItemId(item.getId())
                            .paidFromAmount(amount)
                            .paidFromBalance(BigDecimal.ZERO)
                            .lastModifiedDate(Instant.now())
                            .build()
            );
        }
    }

    private List<PatientServiceAndProduct> attachServicesToPayment(
            PatientPayments payment,
            List<PatientPaymentServiceItemDTO> services
    ) {

        List<PatientServiceAndProduct> rows = services.stream()
                .map(item -> {

                    BigDecimal unitPrice = item.price() == null
                            ? ZERO_AMOUNT
                            : item.price();

                    BigDecimal quantity = BigDecimal.ONE;

                    // ✅ TEMP: حالياً ثابت — لاحقًا من DTO / Waseel
                    BigDecimal factor = BigDecimal.ONE;

                    // ✅ TEMP: لاحقًا من Tax Engine
                    BigDecimal tax = ZERO_AMOUNT;

                    // ✅ الحساب الصحيح
                    BigDecimal gross = unitPrice.multiply(quantity);

                    BigDecimal discounted = gross.multiply(factor);

                    BigDecimal discountAmount = gross.subtract(discounted);

                    BigDecimal net = discounted.add(tax);

                    PatientServiceAndProduct row = PatientServiceAndProduct.builder()
                            .patientId(payment.getPatient().getId())
                            .encounterId(payment.getEncounter().getId())

                            // ✅ Service
                            .serviceId(item.serviceId())
                            .billingItemType(BillingItemTypes.SERVICE)
                            .serviceSource(ServiceSource.ENCOUNTER_DEFAULT_SERVICE)

                            // ✅ Currency
                            .currency(payment.getCurrency())

                            // ✅ Pricing Inputs
                            .quantity(quantity.longValue())
                            .unitPrice(unitPrice)

                            // ✅ Financials (FIXED ✅)
                            .grossAmount(gross)
                            .discountAmount(discountAmount)
                            .taxAmount(tax)
                            .netAmount(net)
                            .totalAmount(net)

                            // ✅ CRITICAL FIX
                            .patientShareAmount(ZERO_AMOUNT)
                            .insuranceShareAmount(ZERO_AMOUNT)

                            // ✅ Payment Tracking
                            .paidAmount(ZERO_AMOUNT)
                            .remainingAmount(
                                    Boolean.TRUE.equals(item.isExempted())
                                            ? ZERO_AMOUNT
                                            : net
                            )

                            .paymentId(payment.getId())
                            .paymentType(payment.getPaymentTypes().name())

                            .paymentStatus(
                                    Boolean.TRUE.equals(item.isExempted())
                                            ? PaymentStatus.PAID
                                            : PaymentStatus.PENDING
                            )

                            .coverageStatus(CoverageStatus.NOT_CHECKED)

                            // ✅ Flags
                            .isDefaultService(Boolean.TRUE)
                            .isExempted(item.isExempted())
                            .isBilled(Boolean.FALSE)

                            // ✅ Future integration
                            .preAuthorizationRequired(Boolean.FALSE)

                            .build();

                    return row;
                })
                .toList();

        return serviceRepository.saveAll(rows);
    }

    private List<PatientServiceAndProduct> buildSkippedServiceRows(
            Long encounterId,
            List<PatientPaymentServiceItemDTO> services
    ) {
        if (services == null || services.isEmpty()) {
            return List.of();
        }

        List<Long> serviceIds = services.stream()
                .map(PatientPaymentServiceItemDTO::serviceId)
                .filter(id -> id != null)
                .toList();

        if (serviceIds.isEmpty()) {
            return serviceRepository.findByEncounterIdAndPaymentIdIsNull(encounterId);
        }

        return serviceRepository.findAllById(serviceIds);
    }

    @Transactional(readOnly = true)
    public BigDecimal getPatientBalance(Long patientId) {
        LOG.debug("[GET_BALANCE] start patientId={}", patientId);

        if (patientId == null) {
            LOG.warn("[GET_BALANCE] rejected: patientId is null");
            throw new BadRequestAlertException(
                    "patientId is required",
                    "patientPayments",
                    "patient.required"
            );
        }

        BigDecimal balance = walletRepository.findById(patientId)
                .map(PatientWallet::getBalance)
                .orElse(ZERO_AMOUNT);

        LOG.debug("[GET_BALANCE] result patientId={} balance={}", patientId, balance);

        return nonNullAmount(balance);
    }

    @Transactional(readOnly = true)
    public PatientLedgerSummaryDTO getPatientLedgerSummary(Long patientId) {
        LOG.debug("[LEDGER_SUMMARY] start patientId={}", patientId);

        if (patientId == null) {
            LOG.warn("[LEDGER_SUMMARY] rejected: patientId is null");
            throw new BadRequestAlertException(
                    "patientId is required",
                    "patientPayments",
                    "patient.required"
            );
        }

        BigDecimal totalDebt =
                nonNullAmount(chargeRepository.sumOpenRemainingByPatient(patientId));

        BigDecimal walletBalance =
                nonNullAmount(walletRepository.findById(patientId)
                        .map(PatientWallet::getBalance)
                        .orElse(ZERO_AMOUNT));

        LOG.info(
                "[LEDGER_SUMMARY] result patientId={} totalDebt={} walletBalance={}",
                patientId,
                totalDebt,
                walletBalance
        );

        return new PatientLedgerSummaryDTO(
                patientId,
                totalDebt,
                walletBalance
        );
    }

    @Transactional(readOnly = true)
    public PatientPaymentFormDTO getPaymentByEncounter(Long encounterId) {
        LOG.debug("[GET_BY_ENCOUNTER] encounterId={}", encounterId);

        PatientPayments payment = paymentRepository.findByEncounter_Id(encounterId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ENCOUNTER] Payment not found for encounterId={}", encounterId);
                    return new NotFoundAlertException(
                            "Payment not found for encounter " + encounterId,
                            "patientPayments",
                            "payment.notfound"
                    );
                });

        List<PatientServiceAndProduct> services =
                serviceRepository.findByPaymentId(payment.getId());

        return toFormDTO(payment, services);
    }
    private PaymentStatus calculatePaymentStatus(Long paymentId) {

        List<PatientPaymentAllocation> allocations =
                allocationRepository.findByPaymentId(paymentId);

        BigDecimal totalAllocated = allocations.stream()
                .map(a -> nonNullAmount(a.getPaidFromAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PatientPayments payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));

        BigDecimal totalDue = nonNullAmount(payment.getDueAmount());

        if (totalAllocated.compareTo(BigDecimal.ZERO) == 0) {
            return PaymentStatus.PENDING;
        }

        if (totalAllocated.compareTo(totalDue) < 0) {
            return PaymentStatus.PARTIALLY_PAID;
        }

        return PaymentStatus.PAID;
    }

    private PatientInsurance resolvePlan(PatientPaymentCreateDTO dto) {
        PatientInsurance plan = null;

        if (dto.paymentTypes() == PaymentTypes.INSURANCE_PLAN) {
            if (dto.planId() == null) {
                LOG.warn(
                        "[CREATE] PatientPayments rejected: planId required paymentTypes={} patientId={} encounterId={}",
                        dto.paymentTypes(),
                        dto.patientId(),
                        dto.encounterId()
                );
                throw new BadRequestAlertException(
                        "planId is required when paymentTypes is INSURANCE_PLAN",
                        "patientPayments",
                        "plan.required"
                );
            }

            plan = insuranceRepository.findById(dto.planId())
                    .orElseThrow(() -> {
                        LOG.warn(
                                "[CREATE] PatientPayments rejected: plan not found planId={} patientId={} encounterId={}",
                                dto.planId(),
                                dto.patientId(),
                                dto.encounterId()
                        );
                        return new NotFoundAlertException(
                                "Plan not found with id " + dto.planId(),
                                "patientPayments",
                                "plan.notfound"
                        );
                    });

        } else if (dto.planId() != null) {
            LOG.warn(
                    "[CREATE] PatientPayments rejected: planId must be null paymentTypes={} planId={} patientId={} encounterId={}",
                    dto.paymentTypes(),
                    dto.planId(),
                    dto.patientId(),
                    dto.encounterId()
            );
            throw new BadRequestAlertException(
                    "planId must be null when paymentTypes is not INSURANCE_PLAN",
                    "patientPayments",
                    "plan.mustBeNull"
            );
        }

        return plan;
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn(
                "[DB_CONSTRAINT] PatientPayments constraint violated rootMessage={}",
                message,
                exception
        );

        if (messageLower.contains("ck_patient_payments_plan_required_when_insurance")) {
            return new BadRequestAlertException(
                    "planId is required when payment method is INSURANCE_COVERAGE.",
                    "patientPayments",
                    "plan.required.byPaymentMethod"
            );
        }

        if (messageLower.contains("ck_patient_payments_card_details_required")) {
            return new BadRequestAlertException(
                    "Card details are required when payment method is CREDIT_DEBIT_CARD.",
                    "patientPayments",
                    "card.details.required"
            );
        }

        if (messageLower.contains("ck_patient_payments_cheque_details_required")) {
            return new BadRequestAlertException(
                    "Cheque details are required when payment method is CHEQUE.",
                    "patientPayments",
                    "cheque.details.required"
            );
        }

        if (messageLower.contains("ck_patient_payments_transfer_details_required")) {
            return new BadRequestAlertException(
                    "Transfer details are required when payment method is BANK_TRANSFER.",
                    "patientPayments",
                    "transfer.details.required"
            );
        }

        if (messageLower.contains("fk_patient_payments_encounter")) {
            return new BadRequestAlertException(
                    "Invalid encounter reference.",
                    "patientPayments",
                    "encounter.fk.invalid"
            );
        }

        if (messageLower.contains("fk_patient_payments_patient")) {
            return new BadRequestAlertException(
                    "Invalid patient reference.",
                    "patientPayments",
                    "patient.fk.invalid"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving patient payment.",
                "patientPayments",
                "db.constraint"
        );
    }
}