package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientCharge;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientPaymentAllocation;
import com.dazzle.asklepios.domain.PatientPayments;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PatientWallet;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.PatientChargeRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;
import com.dazzle.asklepios.repository.PatientPaymentsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PatientWalletRepository;
import com.dazzle.asklepios.service.dto.patientPayments.PatientLedgerSummaryDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentCreateDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentDetailsDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentFormDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentServiceItemDTO;
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

    private final PatientPaymentsRepository paymentRepository;
    private final PatientServiceAndProductRepository serviceRepository;

    private final PatientRepository patientRepository;
    private final PatientEncounterRepository encounterRepository;
    private final PatientInsuranceRepository insuranceRepository;

    private final PatientWalletRepository walletRepository;
    private final PatientChargeRepository chargeRepository;
    private final PatientPaymentAllocationRepository allocationRepository;

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

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

    private BigDecimal computeDueFromServices(Long paymentId) {
        List<PatientServiceAndProduct> services = serviceRepository.findByPaymentId(paymentId);

        return services.stream()
                .filter(serviceRow -> !Boolean.TRUE.equals(serviceRow.getIsExempted()))
                .map(this::resolveServiceAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
    }

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

    private BigDecimal resolveServiceAmountFromDTO(PatientPaymentServiceItemDTO serviceItem) {
        if (serviceItem == null || serviceItem.price() == null) {
            return ZERO_AMOUNT;
        }
        return serviceItem.price();
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

        PatientCharge existing = chargeRepository.findByEncounterId(encounterId).orElse(null);
        if (existing != null) {
            return existing;
        }

        PatientCharge newCharge = PatientCharge.builder()
                .patientId(payment.getPatient().getId())
                .encounterId(encounterId)
                .planId(payment.getPlan() != null ? payment.getPlan().getId() : null)
                .dueAmount(nonNullAmount(dueAmount))
                .remaining(nonNullAmount(dueAmount))
                .currency(payment.getCurrency().name())
                .facilityDefaultCurrency(payment.getFacilityDefaultCurrency().name())
                .createdDate(Instant.now())
                .lastModifiedDate(Instant.now())
                .build();

        try {
            chargeRepository.saveAndFlush(newCharge);
            LOG.debug(
                    "[CHARGE] created for encounterId={} patientId={} due={}",
                    encounterId,
                    payment.getPatient().getId(),
                    nonNullAmount(dueAmount)
            );
        } catch (DataIntegrityViolationException conflict) {
            LOG.warn("[CHARGE] create conflict encounterId={} - reloading", encounterId);
        }

        return chargeRepository.findByEncounterId(encounterId)
                .orElseThrow(() -> new IllegalStateException(
                        "Charge row is missing for encounter " + encounterId
                ));
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

        LOG.info(
                "[LEDGER] start paymentId={} patientId={} encounterId={} method={} type={} addToWallet={} useWalletToSettle={}",
                paymentId,
                patientId,
                encounterId,
                payment.getPaymentMethods(),
                payment.getPaymentTypes(),
                payment.getAddToFreeBalance(),
                payment.getUseBalanceToSettleDebts()
        );

        lockPatientLedger(patientId);

        PatientWallet wallet = getOrCreateWalletLocked(patientId);
        BigDecimal walletBalance = nonNullAmount(wallet.getBalance());

        BigDecimal computedDueAmount = computeDueFromServices(paymentId);
        PatientCharge currentEncounterCharge = getOrCreateChargeForEncounterLocked(payment, computedDueAmount);

        BigDecimal allocatedToCurrentCharge = nonNullAmount(
                allocationRepository.sumAllocatedForCharge(currentEncounterCharge.getId())
        );

        BigDecimal recomputedRemainingForCurrentCharge =
                nonNullAmount(computedDueAmount).subtract(allocatedToCurrentCharge);

        if (recomputedRemainingForCurrentCharge.signum() < 0) {
            BigDecimal overPaid = recomputedRemainingForCurrentCharge.abs();
            walletBalance = walletBalance.add(overPaid);
            recomputedRemainingForCurrentCharge = ZERO_AMOUNT;

            LOG.info(
                    "[LEDGER] overpayment moved to wallet paymentId={} encounterId={} overPaid={} newWalletBalance={}",
                    paymentId,
                    encounterId,
                    overPaid,
                    walletBalance
            );
        }

        currentEncounterCharge.setPlanId(payment.getPlan() != null ? payment.getPlan().getId() : null);
        currentEncounterCharge.setDueAmount(nonNullAmount(computedDueAmount));
        currentEncounterCharge.setRemaining(nonNullAmount(recomputedRemainingForCurrentCharge));
        currentEncounterCharge.setCurrency(payment.getCurrency().name());
        currentEncounterCharge.setFacilityDefaultCurrency(payment.getFacilityDefaultCurrency().name());
        currentEncounterCharge.setLastModifiedDate(Instant.now());

        List<PatientCharge> openCharges = chargeRepository
                .findByPatientIdAndRemainingGreaterThanOrderByCreatedDateAscIdAsc(
                        patientId,
                        ZERO_AMOUNT
                );

        openCharges.sort(Comparator
                .comparing((PatientCharge charge) -> !charge.getEncounterId().equals(encounterId))
                .thenComparing(PatientCharge::getCreatedDate)
                .thenComparing(PatientCharge::getId));

        BigDecimal paymentAmountInFacilityCurrency = getPaidAmountInFacilityCurrency(payment);
        BigDecimal remainingPaymentAmount = nonNullAmount(paymentAmountInFacilityCurrency);

        BigDecimal paidFromPaymentAmount = ZERO_AMOUNT;
        BigDecimal paidFromWalletBalance = ZERO_AMOUNT;

        LOG.debug(
                "[LEDGER] computedDue={} allocatedToCurrentCharge={} recomputedRemainingForCurrentCharge={} paymentAmount={} openChargesCount={}",
                computedDueAmount,
                allocatedToCurrentCharge,
                recomputedRemainingForCurrentCharge,
                paymentAmountInFacilityCurrency,
                openCharges.size()
        );

        for (PatientCharge openCharge : openCharges) {
            if (remainingPaymentAmount.signum() <= 0) {
                break;
            }

            if (nonNullAmount(openCharge.getRemaining()).signum() <= 0) {
                continue;
            }

            BigDecimal amountToPayThisCharge = remainingPaymentAmount.min(openCharge.getRemaining());

            remainingPaymentAmount = remainingPaymentAmount.subtract(amountToPayThisCharge);
            openCharge.setRemaining(openCharge.getRemaining().subtract(amountToPayThisCharge));
            openCharge.setLastModifiedDate(Instant.now());

            PatientPaymentAllocation allocation = allocationRepository
                    .findByPaymentIdAndChargeId(paymentId, openCharge.getId())
                    .orElseGet(() -> PatientPaymentAllocation.builder()
                            .paymentId(paymentId)
                            .chargeId(openCharge.getId())
                            .paidFromAmount(ZERO_AMOUNT)
                            .paidFromBalance(ZERO_AMOUNT)
                            .lastModifiedDate(Instant.now())
                            .build());

            allocation.setPaidFromAmount(
                    nonNullAmount(allocation.getPaidFromAmount()).add(amountToPayThisCharge)
            );
            allocation.setLastModifiedDate(Instant.now());
            allocationRepository.save(allocation);

            paidFromPaymentAmount = paidFromPaymentAmount.add(amountToPayThisCharge);

            LOG.debug(
                    "[ALLOCATE_AMOUNT] paymentId={} chargeId={} encounterId={} paid={} remainingPayment={} chargeRemaining={}",
                    paymentId,
                    openCharge.getId(),
                    openCharge.getEncounterId(),
                    amountToPayThisCharge,
                    remainingPaymentAmount,
                    openCharge.getRemaining()
            );
        }

        BigDecimal refunds = ZERO_AMOUNT;

        if (remainingPaymentAmount.signum() > 0) {
            if (Boolean.TRUE.equals(payment.getAddToFreeBalance())) {
                walletBalance = walletBalance.add(remainingPaymentAmount);

                LOG.info(
                        "[LEDGER] leftover added to wallet paymentId={} leftover={} newWalletBalance={}",
                        paymentId,
                        remainingPaymentAmount,
                        walletBalance
                );
            } else {
                refunds = remainingPaymentAmount;

                LOG.info(
                        "[LEDGER] leftover refunded paymentId={} refunds={}",
                        paymentId,
                        refunds
                );
            }
        }

        boolean useBalanceToSettleDebts = Boolean.TRUE.equals(payment.getUseBalanceToSettleDebts());

        if (useBalanceToSettleDebts && walletBalance.signum() > 0) {
            for (PatientCharge openCharge : openCharges) {
                if (walletBalance.signum() <= 0) {
                    break;
                }

                if (nonNullAmount(openCharge.getRemaining()).signum() <= 0) {
                    continue;
                }

                BigDecimal amountToPayThisCharge = walletBalance.min(openCharge.getRemaining());

                walletBalance = walletBalance.subtract(amountToPayThisCharge);
                openCharge.setRemaining(openCharge.getRemaining().subtract(amountToPayThisCharge));
                openCharge.setLastModifiedDate(Instant.now());

                PatientPaymentAllocation allocation = allocationRepository
                        .findByPaymentIdAndChargeId(paymentId, openCharge.getId())
                        .orElseGet(() -> PatientPaymentAllocation.builder()
                                .paymentId(paymentId)
                                .chargeId(openCharge.getId())
                                .paidFromAmount(ZERO_AMOUNT)
                                .paidFromBalance(ZERO_AMOUNT)
                                .lastModifiedDate(Instant.now())
                                .build());

                allocation.setPaidFromBalance(
                        nonNullAmount(allocation.getPaidFromBalance()).add(amountToPayThisCharge)
                );
                allocation.setLastModifiedDate(Instant.now());
                allocationRepository.save(allocation);

                paidFromWalletBalance = paidFromWalletBalance.add(amountToPayThisCharge);

                LOG.debug(
                        "[ALLOCATE_WALLET] paymentId={} chargeId={} encounterId={} paid={} walletRemaining={} chargeRemaining={}",
                        paymentId,
                        openCharge.getId(),
                        openCharge.getEncounterId(),
                        amountToPayThisCharge,
                        walletBalance,
                        openCharge.getRemaining()
                );
            }
        }

        chargeRepository.saveAll(openCharges);
        chargeRepository.save(currentEncounterCharge);

        wallet.setBalance(walletBalance.max(ZERO_AMOUNT));
        wallet.setLastModifiedDate(Instant.now());
        walletRepository.save(wallet);

        payment.setDueAmount(nonNullAmount(computedDueAmount));
        payment.setRemaining(
                openCharges.stream()
                        .filter(charge -> charge.getEncounterId().equals(encounterId))
                        .findFirst()
                        .map(PatientCharge::getRemaining)
                        .orElse(currentEncounterCharge.getRemaining())
        );
        payment.setPaidFromAmount(paidFromPaymentAmount);
        payment.setPaidFromBalance(paidFromWalletBalance);
        payment.setRefunds(refunds);
        payment.setPatientBalance(wallet.getBalance());

        paymentRepository.save(payment);

        syncPaymentServicesAfterLedger(paymentId);

        jdbcTemplate.update("""
            update patient_payments
            set patient_balance = ?
            where patient_id = ?
        """, wallet.getBalance(), patientId);

        LOG.info(
                "[LEDGER] done paymentId={} patientId={} encounterId={} due={} paidFromAmount={} paidFromBalance={} refunds={} walletBalance={} paymentRemaining={}",
                paymentId,
                patientId,
                encounterId,
                payment.getDueAmount(),
                paidFromPaymentAmount,
                paidFromWalletBalance,
                refunds,
                wallet.getBalance(),
                payment.getRemaining()
        );
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

        try {
            applyLedgerForPayment(saved.getId());
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[LEDGER] failed (constraint) paymentId={}", saved.getId(), ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[LEDGER] failed (unexpected) paymentId={}", saved.getId(), ex);
            throw ex;
        }

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

        paymentRepository.findByEncounter_Id(dto.encounterId()).ifPresent(existingPayment -> {
            LOG.warn(
                    "[CREATE] PatientPayments rejected: duplicate payment encounterId={} paymentId={}",
                    dto.encounterId(),
                    existingPayment.getId()
            );
            throw new BadRequestAlertException(
                    "Payment already exists for this encounter",
                    "patientPayments",
                    "payment.duplicate.encounter"
            );
        });

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
                .build();

        try {
            PatientPayments saved = paymentRepository.saveAndFlush(payment);

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

            if (encounter.getEncounterType().equals(EncounterType.EMERGENCY)) {
                encounter.setStatus(EncounterStatus.WAITING_TRIAGE);
            } else {
                encounter.setStatus(EncounterStatus.NEW);
            }

            encounterRepository.saveAndFlush(encounter);

            return finalizeAndReturnDetails(saved);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] PatientPayments failed (constraint) payload={}", dto, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[CREATE] PatientPayments failed (unexpected) payload={}", dto, ex);
            throw ex;
        }
    }

    private List<PatientServiceAndProduct> attachServicesToPayment(
            PatientPayments payment,
            List<PatientPaymentServiceItemDTO> services
    ) {

        List<PatientServiceAndProduct> rows = services.stream()
                .map(item -> {

                    BigDecimal amount = item.price() == null
                            ? ZERO_AMOUNT
                            : item.price();

                    PatientServiceAndProduct row = PatientServiceAndProduct.builder()
                            .patientId(payment.getPatient().getId())
                            .encounterId(payment.getEncounter().getId())

                            // Service
                            .serviceId(item.serviceId())
                            .billingItemType(BillingItemTypes.SERVICE)

                            // عدليها حسب الـ enum الموجود عندك
                            .serviceSource(ServiceSource.ENCOUNTER_DEFAULT_SERVICE)

                            .currency(payment.getCurrency())

                            .quantity(1L)
                            .unitPrice(amount)

                            .discountAmount(ZERO_AMOUNT)
                            .exemptionAmount(ZERO_AMOUNT)
                            .taxAmount(ZERO_AMOUNT)

                            .grossAmount(amount)
                            .netAmount(amount)
                            .totalAmount(amount)

                            .patientShareAmount(amount)
                            .insuranceShareAmount(ZERO_AMOUNT)

                            .paidAmount(ZERO_AMOUNT)
                            .remainingAmount(
                                    Boolean.TRUE.equals(item.isExempted())
                                            ? ZERO_AMOUNT
                                            : amount
                            )

                            .paymentId(payment.getId())
                            .paymentType(payment.getPaymentTypes().name())

                            .paymentStatus(
                                    Boolean.TRUE.equals(item.isExempted())
                                            ? PaymentStatus.PAID
                                            : PaymentStatus.PENDING
                            )

                            .coverageStatus(CoverageStatus.NOT_CHECKED)

                            .isDefaultService(Boolean.TRUE)
                            .isExempted(item.isExempted())

                            .isBilled(Boolean.FALSE)
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