package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.BillingPaymentTransactionRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingPaymentReservationResult;
import com.dazzle.asklepios.service.dto.billing.BillingPaymentResult;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.CreateAdvancePaymentRequest;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingPaymentService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingPaymentService.class
            );

    private static final String ENTITY_NAME =
            "billingPayment";

    private static final int MONEY_SCALE = 4;

    private final BillingPaymentRepository
            billingPaymentRepository;

    private final BillingResponsibilityService
            billingResponsibilityService;

    private final BillingPaymentTransactionRepository
            billingPaymentTransactionRepository;

    private final PatientRepository
            patientRepository;

    private final PatientEncounterRepository
            patientEncounterRepository;

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final BillingWalletService
            billingWalletService;

    private final BillingReservationService
            billingReservationService;

    private final BillingChargeService
            billingChargeService;

    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public BillingPaymentResult createAdvancePayment(
            CreateAdvancePaymentRequest request
    ) {
        validateRequest(request);

        String paymentIdempotencyKey =
                buildPaymentIdempotencyKey(
                        request.requestId()
                );

        BillingPayment existing =
                billingPaymentRepository
                        .findByIdempotencyKey(
                                paymentIdempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            return loadExistingResult(existing);
        }

        Patient patient =
                patientRepository
                        .findById(request.patientId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient not found with id "
                                                + request.patientId(),
                                        ENTITY_NAME,
                                        "patient.notfound"
                                )
                        );

        PatientEncounter encounter =
                loadAndValidateEncounter(
                        request,
                        patient
                );

        /*
         * Create or lock the wallet first.
         */
        BillingWallet wallet =
                billingWalletService
                        .getOrCreateWallet(
                                request.patientId(),
                                request.currency()
                        );

        BillingPayment payment =
                createPayment(
                        request,
                        wallet,
                        patient,
                        encounter,
                        paymentIdempotencyKey
                );

        BillingPaymentTransaction paymentTransaction =
                createPaymentTransaction(
                        request,
                        payment
                );

        /*
         * The payment is confirmed by the statuses supplied in the request.
         * Only confirmed/successful requests should call this operation.
         *
         * Wallet is credited once inside this transaction.
         */
        BillingWallet creditedWallet =
                billingWalletService.credit(
                        request.patientId(),
                        request.currency(),
                        request.amount()
                );

        List<BillingPaymentReservationResult>
                reservationResults =
                createServiceReservations(
                        request,
                        payment,
                        paymentTransaction
                );

        BigDecimal totalReserved =
                reservationResults.stream()
                        .map(
                                BillingPaymentReservationResult::
                                        reservedAmount
                        )
                        .map(this::money)
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        BillingWallet finalWallet =
                billingWalletService.lockWallet(
                        request.patientId(),
                        request.currency()
                );

        LOG.info(
                "[CREATE_ADVANCE] Advance payment created "
                        + "paymentId={} transactionId={} patientId={} "
                        + "amount={} available={} reserved={} serviceCount={}",
                payment.getId(),
                paymentTransaction.getId(),
                request.patientId(),
                request.amount(),
                finalWallet.getAvailableBalance(),
                finalWallet.getReservedBalance(),
                reservationResults.size()
        );

        return buildResult(
                payment,
                paymentTransaction,
                finalWallet,
                totalReserved,
                reservationResults
        );
    }

    @Transactional(readOnly = true)
    public BillingPaymentResult findById(
            Long paymentId
    ) {
        if (paymentId == null) {
            throw new BadRequestAlertException(
                    "Payment ID is required.",
                    ENTITY_NAME,
                    "paymentId.required"
            );
        }

        BillingPayment payment =
                billingPaymentRepository
                        .findById(paymentId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing payment not found with id "
                                                + paymentId,
                                        ENTITY_NAME,
                                        "payment.notfound"
                                )
                        );

        return loadExistingResult(payment);
    }

    private BillingPayment createPayment(
            CreateAdvancePaymentRequest request,
            BillingWallet wallet,
            Patient patient,
            PatientEncounter encounter,
            String idempotencyKey
    ) {
        BillingPayment payment =
                BillingPayment.builder()
                        .paymentNumber(
                                generatePaymentNumber()
                        )
                        .wallet(wallet)
                        .patient(patient)
                        .encounter(encounter)
                        .paymentCategory(
                                request.paymentCategory()
                        )
                        .payerType(
                                request.payerType()
                        )
                        .payerId(
                                request.payerId()
                        )
                        .amount(
                                money(request.amount())
                        )
                        .currency(
                                request.currency()
                        )
                        .status(
                                request.paymentStatus()
                        )
                        .paymentDate(
                                Instant.now()
                        )
                        .receiptNumber(
                                trimToNull(
                                        request.receiptNumber()
                                )
                        )
                        .externalReference(
                                trimToNull(
                                        request.externalReference()
                                )
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .notes(
                                trimToNull(
                                        request.notes()
                                )
                        )
                        .build();

        try {
            BillingPayment saved =
                    billingPaymentRepository
                            .saveAndFlush(payment);

            LOG.info(
                    "[CREATE_PAYMENT] Payment created "
                            + "paymentId={} paymentNumber={} amount={} status={}",
                    saved.getId(),
                    saved.getPaymentNumber(),
                    saved.getAmount(),
                    saved.getStatus()
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingPayment concurrent =
                    billingPaymentRepository
                            .findByIdempotencyKey(
                                    idempotencyKey
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            LOG.error(
                    "[CREATE_PAYMENT] Payment creation failed "
                            + "patientId={} amount={} requestId={}",
                    request.patientId(),
                    request.amount(),
                    request.requestId(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to create billing payment.",
                    ENTITY_NAME,
                    "payment.create.failed"
            );
        }
    }

    private BillingPaymentTransaction
    createPaymentTransaction(
            CreateAdvancePaymentRequest request,
            BillingPayment payment
    ) {
        String idempotencyKey =
                payment.getIdempotencyKey()
                        + ":TRANSACTION";

        BillingPaymentTransaction existing =
                billingPaymentTransactionRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {
            return existing;
        }

        BillingPaymentTransaction transaction =
                BillingPaymentTransaction.builder()
                        .transactionNumber(
                                generateTransactionNumber()
                        )
                        .payment(payment)
                        .parentTransaction(null)
                        .transactionType(
                                request.transactionType()
                        )
                        .paymentMethodId(
                                request.paymentMethodId()
                        )
                        .paymentMethodCode(
                                request.paymentMethodCode()
                                        .trim()
                        )
                        .amount(
                                money(request.amount())
                        )
                        .currency(
                                request.currency()
                        )
                        .status(
                                request.transactionStatus()
                        )
                        .transactionDate(
                                Instant.now()
                        )
                        .externalReference(
                                trimToNull(
                                        request.externalReference()
                                )
                        )
                        .authorizationCode(
                                trimToNull(
                                        request.authorizationCode()
                                )
                        )
                        .processorReference(
                                trimToNull(
                                        request.processorReference()
                                )
                        )
                        .cardLastFour(
                                trimToNull(
                                        request.cardLastFour()
                                )
                        )
                        .bankReference(
                                trimToNull(
                                        request.bankReference()
                                )
                        )
                        .cashRegisterId(
                                request.cashRegisterId()
                        )
                        .failureCode(null)
                        .failureReason(null)
                        .processorResponse(
                                buildProcessorResponse(
                                        request
                                )
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .notes(
                                trimToNull(
                                        request.notes()
                                )
                        )
                        .build();

        try {
            BillingPaymentTransaction saved =
                    billingPaymentTransactionRepository
                            .saveAndFlush(transaction);

            LOG.info(
                    "[CREATE_TRANSACTION] Payment transaction created "
                            + "transactionId={} transactionNumber={} "
                            + "paymentId={} amount={} status={}",
                    saved.getId(),
                    saved.getTransactionNumber(),
                    payment.getId(),
                    saved.getAmount(),
                    saved.getStatus()
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            BillingPaymentTransaction concurrent =
                    billingPaymentTransactionRepository
                            .findByIdempotencyKey(
                                    idempotencyKey
                            )
                            .orElse(null);

            if (concurrent != null) {
                return concurrent;
            }

            throw new BadRequestAlertException(
                    "Unable to create billing payment transaction.",
                    ENTITY_NAME,
                    "paymentTransaction.create.failed"
            );
        }
    }

    private List<BillingPaymentReservationResult>
    createServiceReservations(
            CreateAdvancePaymentRequest request,
            BillingPayment payment,
            BillingPaymentTransaction paymentTransaction
    ) {
        List<Long> requestedItemIds =
                request.patientServiceProductIds();

        if (requestedItemIds == null
                || requestedItemIds.isEmpty()) {

            /*
             * Patient-level advance:
             * no reservation is created.
             */
            return List.of();
        }

        Set<Long> uniqueItemIds =
                new LinkedHashSet<>(
                        requestedItemIds
                );

        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository
                        .findAllById(uniqueItemIds)
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        PatientServiceAndProduct::getId
                                )
                        )
                        .toList();

        if (items.size() != uniqueItemIds.size()) {
            throw new BadRequestAlertException(
                    "One or more patient service/product records were not found.",
                    ENTITY_NAME,
                    "patientServiceProduct.notfound"
            );
        }

        List<BillingPaymentReservationResult> results =
                new ArrayList<>();

        for (PatientServiceAndProduct item : items) {
            validateReservationItem(
                    request,
                    item
            );

            BillingProcessingContext context =
                    BillingProcessingContext.builder()
                            .transactionGroupId(
                                    UUID.randomUUID()
                            )
                            .idempotencyKey(
                                    payment.getIdempotencyKey()
                                            + ":PSP:"
                                            + item.getId()
                            )
                            .patientServiceProduct(
                                    item
                            )
                            .build();

            /*
             * Loads the existing active charge line.
             */
            billingChargeService
                    .loadExistingChargeLine(
                            context
                    );

            /*
             * Responsibility should already have been created by
             * BillingResponsibilityService during charge creation.
             *
             * Load it into context before reservation.
             */
            billingResponsibilityService
                    .loadPatientResponsibility(context);

            BigDecimal patientAmount =
                    money(
                            context
                                    .getPatientResponsibilityAmount()
                    );

            BillingReservation reservation =
                    billingReservationService.reserve(
                            context,
                            payment,
                            paymentTransaction
                    );

            BigDecimal reservedAmount =
                    reservation == null
                            ? zero()
                            : money(
                            reservation
                                    .getRemainingReservedAmount()
                    );

            BigDecimal uncoveredAmount =
                    patientAmount
                            .subtract(
                                    reservedAmount
                            )
                            .max(zero());

            results.add(
                    new BillingPaymentReservationResult(
                            item.getId(),
                            reservation == null
                                    ? null
                                    : reservation.getId(),
                            reservation == null
                                    ? null
                                    : reservation
                                    .getReservationNumber(),
                            patientAmount,
                            reservedAmount,
                            uncoveredAmount,
                            uncoveredAmount.signum() == 0
                    )
            );
        }

        return results;
    }


    private PatientEncounter loadAndValidateEncounter(
            CreateAdvancePaymentRequest request,
            Patient patient
    ) {
        if (request.encounterId() == null) {
            return null;
        }

        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(
                                request.encounterId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id "
                                                + request.encounterId(),
                                        ENTITY_NAME,
                                        "encounter.notfound"
                                )
                        );

        if (encounter.getPatient() == null
                || encounter.getPatient().getId() == null
                || !encounter.getPatient()
                .getId()
                .equals(patient.getId())) {

            throw new BadRequestAlertException(
                    "Encounter does not belong to the patient.",
                    ENTITY_NAME,
                    "encounter.patient.mismatch"
            );
        }

        return encounter;
    }

    private void validateReservationItem(
            CreateAdvancePaymentRequest request,
            PatientServiceAndProduct item
    ) {
        if (!item.getPatientId()
                .equals(request.patientId())) {
            throw new BadRequestAlertException(
                    "Patient service/product does not belong to the payment patient.",
                    ENTITY_NAME,
                    "patientServiceProduct.patient.mismatch"
            );
        }

        if (item.getCurrency()
                != request.currency()) {
            throw new BadRequestAlertException(
                    "Patient service/product currency does not match payment currency.",
                    ENTITY_NAME,
                    "patientServiceProduct.currency.mismatch"
            );
        }

        if (request.encounterId() != null
                && !request.encounterId()
                .equals(item.getEncounterId())) {
            throw new BadRequestAlertException(
                    "Patient service/product does not belong to the payment encounter.",
                    ENTITY_NAME,
                    "patientServiceProduct.encounter.mismatch"
            );
        }
    }

    private BillingPaymentResult loadExistingResult(
            BillingPayment payment
    ) {
        List<BillingPaymentTransaction> transactions =
                billingPaymentTransactionRepository
                        .findAllByPayment_IdOrderByTransactionDateAscIdAsc(
                                payment.getId()
                        );

        BillingPaymentTransaction transaction =
                transactions.isEmpty()
                        ? null
                        : transactions.get(
                        transactions.size() - 1
                );

        BillingWallet wallet =
                payment.getWallet();

        return buildResult(
                payment,
                transaction,
                wallet,
                money(wallet.getReservedBalance()),
                List.of()
        );
    }

    private BillingPaymentResult buildResult(
            BillingPayment payment,
            BillingPaymentTransaction transaction,
            BillingWallet wallet,
            BigDecimal totalReserved,
            List<BillingPaymentReservationResult> reservations
    ) {
        return new BillingPaymentResult(
                payment.getId(),
                payment.getPaymentNumber(),

                transaction == null
                        ? null
                        : transaction.getId(),

                transaction == null
                        ? null
                        : transaction.getTransactionNumber(),

                wallet.getId(),

                money(payment.getAmount()),

                money(
                        wallet.getAvailableBalance()
                ),

                money(
                        wallet.getReservedBalance()
                ),

                money(
                        wallet.getConsumedAmount()
                ),

                money(
                        wallet.getRefundedAmount()
                ),

                money(totalReserved),

                payment.getCurrency(),

                payment.getStatus(),

                transaction == null
                        ? null
                        : transaction.getStatus(),

                reservations
        );
    }

    private void validateRequest(
            CreateAdvancePaymentRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Advance payment request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.patientId() == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patientId.required"
            );
        }

        if (request.amount() == null
                || request.amount().signum() <= 0) {
            throw new BadRequestAlertException(
                    "Payment amount must be greater than zero.",
                    ENTITY_NAME,
                    "amount.invalid"
            );
        }

        if (request.currency() == null) {
            throw new BadRequestAlertException(
                    "Currency is required.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        if (request.paymentCategory() == null) {
            throw new BadRequestAlertException(
                    "Payment category is required.",
                    ENTITY_NAME,
                    "paymentCategory.required"
            );
        }

        if (request.payerType() == null) {
            throw new BadRequestAlertException(
                    "Payer type is required.",
                    ENTITY_NAME,
                    "payerType.required"
            );
        }

        if (request.paymentMethodId() == null) {
            throw new BadRequestAlertException(
                    "Payment method ID is required.",
                    ENTITY_NAME,
                    "paymentMethodId.required"
            );
        }

        if (request.paymentMethodCode() == null
                || request.paymentMethodCode()
                .isBlank()) {
            throw new BadRequestAlertException(
                    "Payment method code is required.",
                    ENTITY_NAME,
                    "paymentMethodCode.required"
            );
        }

        if (request.paymentStatus() == null) {
            throw new BadRequestAlertException(
                    "Payment status is required.",
                    ENTITY_NAME,
                    "paymentStatus.required"
            );
        }

        if (request.transactionType() == null) {
            throw new BadRequestAlertException(
                    "Payment transaction type is required.",
                    ENTITY_NAME,
                    "transactionType.required"
            );
        }

        if (request.transactionStatus() == null) {
            throw new BadRequestAlertException(
                    "Payment transaction status is required.",
                    ENTITY_NAME,
                    "transactionStatus.required"
            );
        }

        if (request.requestId() == null
                || request.requestId().isBlank()) {
            throw new BadRequestAlertException(
                    "Request ID is required.",
                    ENTITY_NAME,
                    "requestId.required"
            );
        }
    }

    private ObjectNode buildProcessorResponse(
            CreateAdvancePaymentRequest request
    ) {
        ObjectNode node =
                objectMapper.createObjectNode();

        node.put(
                "paymentMethodId",
                request.paymentMethodId()
        );

        node.put(
                "paymentMethodCode",
                request.paymentMethodCode()
        );

        if (request.externalReference() != null) {
            node.put(
                    "externalReference",
                    request.externalReference()
            );
        }

        if (request.processorReference() != null) {
            node.put(
                    "processorReference",
                    request.processorReference()
            );
        }

        return node;
    }

    private String buildPaymentIdempotencyKey(
            String requestId
    ) {
        return "ADVANCE_PAYMENT:"
                + requestId.trim();
    }

    private String generatePaymentNumber() {
        return "PAY-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private String generateTransactionNumber() {
        return "PTX-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return value == null
                ? zero()
                : value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private String trimToNull(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}