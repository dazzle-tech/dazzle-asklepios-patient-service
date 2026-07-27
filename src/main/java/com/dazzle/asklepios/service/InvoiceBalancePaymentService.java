package com.dazzle.asklepios.service;



import com.dazzle.asklepios.domain.FinancialDocument;

import com.dazzle.asklepios.domain.FinancialDocumentItem;

import com.dazzle.asklepios.domain.FinancialDocumentItemStatus;

import com.dazzle.asklepios.domain.PatientEncounter;

import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;

import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;

import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;

import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionStatus;

import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionType;

import com.dazzle.asklepios.domain.enumeration.billing.PayerType;

import com.dazzle.asklepios.domain.enumeration.billing.PaymentCategory;

import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;

import com.dazzle.asklepios.repository.FinancialDocumentRepository;

import com.dazzle.asklepios.repository.PatientEncounterRepository;

import com.dazzle.asklepios.service.dto.billing.BillingPaymentResult;

import com.dazzle.asklepios.service.dto.billing.CollectInvoiceBalanceRequest;

import com.dazzle.asklepios.service.dto.billing.CollectInvoiceBalanceResult;

import com.dazzle.asklepios.service.dto.billing.CreateAdvancePaymentRequest;

import com.dazzle.asklepios.service.dto.billing.SyncInvoicePaymentsResult;

import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;

import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.math.BigDecimal;

import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;



@Service

@RequiredArgsConstructor

public class InvoiceBalancePaymentService {



    private static final Logger LOG =

            LoggerFactory.getLogger(InvoiceBalancePaymentService.class);



    private static final String ENTITY_NAME = "invoiceBalancePayment";



    private static final int MONEY_SCALE = 4;



    private final FinancialDocumentRepository financialDocumentRepository;

    private final FinancialDocumentItemRepository financialDocumentItemRepository;

    private final FinancialDocumentBalanceService financialDocumentBalanceService;

    private final FinancialDocumentStatusService financialDocumentStatusService;

    private final InvoiceChargePaymentSyncService invoiceChargePaymentSyncService;

    private final BillingPaymentService billingPaymentService;

    private final BillingWalletService billingWalletService;

    private final PatientEncounterRepository patientEncounterRepository;



    @Transactional(rollbackFor = Exception.class)

    public CollectInvoiceBalanceResult collectBalance(

            Long invoiceId,

            CollectInvoiceBalanceRequest request

    ) {

        FinancialDocument invoice = loadInvoice(invoiceId);

        PatientEncounter encounter = requireEncounter(invoice.getEncounterId());



        BigDecimal outstanding =

                money(

                        financialDocumentBalanceService.calculateOutstanding(

                                invoiceId

                        )

                );



        if (outstanding.signum() <= 0) {

            throw new BadRequestAlertException(

                    "Invoice has no outstanding balance.",

                    ENTITY_NAME,

                    "invoice.alreadyPaid"

            );

        }



        BigDecimal amountToCollect =

                request.amount() == null

                        ? outstanding

                        : money(request.amount());



        if (amountToCollect.compareTo(outstanding) > 0) {

            throw new BadRequestAlertException(

                    "Payment amount exceeds invoice outstanding balance.",

                    ENTITY_NAME,

                    "invoice.overpayment"

            );

        }



        if (amountToCollect.signum() <= 0) {

            throw new BadRequestAlertException(

                    "Payment amount must be greater than zero.",

                    ENTITY_NAME,

                    "invoice.invalidAmount"

            );

        }



        PaymentCategory paymentCategory =

                resolvePaymentCategory(request.paymentMethodCode());



        CreateAdvancePaymentRequest paymentRequest =

                new CreateAdvancePaymentRequest(

                        invoice.getPatientId(),

                        encounter.getId(),

                        paymentCategory,

                        PayerType.PATIENT,

                        null,

                        amountToCollect,

                        invoice.getCurrency(),

                        request.paymentMethodId(),

                        request.paymentMethodCode(),

                        BillingPaymentStatus.COMPLETED,

                        BillingPaymentTransactionType.PAYMENT,

                        BillingPaymentTransactionStatus.SUCCESS,

                        null,

                        null,

                        null,

                        null,

                        null,

                        null,

                        null,

                        request.notes() != null

                                ? request.notes()

                                : "Invoice balance "

                                        + invoice.getDocumentNumber(),

                        List.of(),

                        request.requestId()

                );



        BillingPaymentResult paymentResult =

                billingPaymentService.createAdvancePayment(

                        paymentRequest

                );



        if (!PaymentCategory.WALLET.equals(paymentCategory)) {

            billingWalletService.consumeAvailable(

                    billingWalletService.getOrCreateWallet(

                            invoice.getPatientId(),

                            invoice.getCurrency()

                    ),

                    amountToCollect

            );

        }



        List<FinancialDocumentItem> items =
                loadItemsForBalancePayment(invoiceId);

        invoiceChargePaymentSyncService.syncPreInvoicePayments(
                items.stream()
                        .filter(item ->
                                item.getDocument().getId().equals(invoiceId)
                        )
                        .toList()
        );

        distributePayment(items, amountToCollect);

        financialDocumentItemRepository.saveAll(items);



        FinancialDocumentStatus status =

                financialDocumentStatusService.calculate(invoiceId);

        invoice.setStatus(status);

        financialDocumentRepository.save(invoice);



        BigDecimal remainingOutstanding =

                money(

                        financialDocumentBalanceService.calculateOutstanding(

                                invoiceId

                        )

                );

        BigDecimal paidAmount =

                money(invoice.getTotalAmount())

                        .subtract(remainingOutstanding)

                        .max(BigDecimal.ZERO);



        LOG.info(

                "[INVOICE_PAYMENT] invoiceId={} paymentId={} collected={} outstanding={} status={}",

                invoiceId,

                paymentResult.paymentId(),

                amountToCollect,

                remainingOutstanding,

                status

        );



        return new CollectInvoiceBalanceResult(

                invoice.getId(),

                invoice.getDocumentNumber(),

                invoice.getCurrency(),

                amountToCollect,

                paidAmount,

                remainingOutstanding,

                status,

                paymentResult.paymentId(),

                paymentResult.paymentNumber(),

                paymentResult.transactionNumber(),

                paymentResult.walletAvailableBalance()

        );

    }



    @Transactional(rollbackFor = Exception.class)

    public SyncInvoicePaymentsResult syncChargePayments(Long invoiceId) {

        FinancialDocument invoice = loadInvoice(invoiceId);



        List<FinancialDocumentItem> items =

                financialDocumentItemRepository

                        .findByDocument_Id(invoiceId)

                        .stream()

                        .sorted(Comparator.comparing(FinancialDocumentItem::getId))

                        .toList();



        invoiceChargePaymentSyncService.syncPreInvoicePayments(items);

        financialDocumentItemRepository.saveAll(items);



        FinancialDocumentStatus status =

                financialDocumentStatusService.calculate(invoiceId);

        invoice.setStatus(status);

        financialDocumentRepository.save(invoice);



        BigDecimal outstanding =

                money(

                        financialDocumentBalanceService.calculateOutstanding(

                                invoiceId

                        )

                );

        BigDecimal paidAmount =

                money(invoice.getTotalAmount())

                        .subtract(outstanding)

                        .max(BigDecimal.ZERO);



        return new SyncInvoicePaymentsResult(

                invoiceId,

                paidAmount,

                outstanding

        );

    }



    private PaymentCategory resolvePaymentCategory(String paymentMethodCode) {

        if (paymentMethodCode == null || paymentMethodCode.isBlank()) {

            return PaymentCategory.CASH;

        }



        return switch (paymentMethodCode.trim().toUpperCase()) {

            case "DEDUCT_FROM_FREE_BALANCE" -> PaymentCategory.WALLET;

            case "CREDIT_DEBIT_CARD" -> PaymentCategory.CARD;

            case "BANK_TRANSFER" -> PaymentCategory.BANK_TRANSFER;

            case "CHEQUE" -> PaymentCategory.CHEQUE;

            default -> PaymentCategory.CASH;

        };

    }



    private PatientEncounter requireEncounter(Long encounterId) {

        if (encounterId == null) {

            throw new BadRequestAlertException(

                    "Invoice is not linked to an encounter.",

                    ENTITY_NAME,

                    "invoice.encounter.missing"

            );

        }



        return patientEncounterRepository

                .findById(encounterId)

                .orElseThrow(() ->

                        new NotFoundAlertException(

                                "Encounter not found with id " + encounterId,

                                ENTITY_NAME,

                                "encounter.notfound"

                        )

                );

    }



    private List<FinancialDocumentItem> loadItemsForBalancePayment(
            Long invoiceId
    ) {
        List<FinancialDocumentItem> invoiceItems =
                financialDocumentItemRepository
                        .findByDocument_Id(invoiceId);

        List<FinancialDocumentItem> debitNoteItems =
                financialDocumentRepository
                        .findAllByParentDocumentId(invoiceId)
                        .stream()
                        .filter(document ->
                                document.getDocumentType()
                                        == FinancialDocumentType.DEBIT_NOTE
                        )
                        .flatMap(document ->
                                financialDocumentItemRepository
                                        .findByDocument_Id(document.getId())
                                        .stream()
                        )
                        .toList();

        return Stream.concat(
                        invoiceItems.stream(),
                        debitNoteItems.stream()
                )
                .sorted(Comparator.comparing(FinancialDocumentItem::getId))
                .toList();
    }

    private void distributePayment(

            List<FinancialDocumentItem> items,

            BigDecimal amountToCollect

    ) {

        BigDecimal paymentLeft =

                distributeByRemainingBalance(items, amountToCollect);



        if (paymentLeft.signum() <= 0) {

            return;

        }



        List<FinancialDocumentItem> invoiceItems =

                items.stream()

                        .filter(item ->

                                item.getDocument().getDocumentType()

                                        == FinancialDocumentType.INVOICE

                        )

                        .toList();



        distributeInvoiceLevelBalance(invoiceItems, paymentLeft);

    }

    private BigDecimal distributeByRemainingBalance(

            List<FinancialDocumentItem> items,

            BigDecimal amountToCollect

    ) {

        BigDecimal totalRemaining =

                items.stream()

                        .map(FinancialDocumentItem::getRemainingAmount)

                        .map(this::money)

                        .reduce(BigDecimal.ZERO, BigDecimal::add);



        if (totalRemaining.signum() <= 0) {

            return amountToCollect;

        }



        BigDecimal paymentLeft = amountToCollect;



        List<FinancialDocumentItem> payableItems =

                items.stream()

                        .filter(item -> money(item.getRemainingAmount()).signum() > 0)

                        .toList();



        for (int index = 0; index < payableItems.size(); index++) {

            FinancialDocumentItem item = payableItems.get(index);

            BigDecimal lineRemaining = money(item.getRemainingAmount());

            boolean isLast = index == payableItems.size() - 1;



            BigDecimal linePayment =

                    isLast

                            ? paymentLeft.min(lineRemaining)

                            : amountToCollect

                                    .multiply(

                                            lineRemaining.divide(

                                                    totalRemaining,

                                                    MONEY_SCALE,

                                                    RoundingMode.HALF_UP

                                            )

                                    )

                                    .setScale(

                                            MONEY_SCALE,

                                            RoundingMode.HALF_UP

                                    )

                                    .min(lineRemaining)

                                    .min(paymentLeft);



            applyLinePayment(item, linePayment);



            paymentLeft = paymentLeft.subtract(linePayment);

        }



        return paymentLeft;

    }

    /**

     * Applies payment when line remaining is zero but the invoice document still

     * has an outstanding balance (e.g. invoice-level tax/discount delta).

     */

    private void distributeInvoiceLevelBalance(

            List<FinancialDocumentItem> items,

            BigDecimal amountToCollect

    ) {

        BigDecimal totalWeight =

                items.stream()

                        .map(this::paymentDistributionWeight)

                        .reduce(BigDecimal.ZERO, BigDecimal::add);



        if (totalWeight.signum() <= 0) {

            throw new BadRequestAlertException(

                    "Invoice lines have no remaining balance.",

                    ENTITY_NAME,

                    "invoice.noRemainingLines"

            );

        }



        BigDecimal paymentLeft = amountToCollect;



        List<FinancialDocumentItem> weightedItems =

                new ArrayList<>(items);



        for (int index = 0; index < weightedItems.size(); index++) {

            FinancialDocumentItem item = weightedItems.get(index);

            BigDecimal weight = paymentDistributionWeight(item);

            boolean isLast = index == weightedItems.size() - 1;



            BigDecimal linePayment =

                    isLast

                            ? paymentLeft

                            : amountToCollect

                                    .multiply(

                                            weight.divide(

                                                    totalWeight,

                                                    MONEY_SCALE,

                                                    RoundingMode.HALF_UP

                                            )

                                    )

                                    .setScale(

                                            MONEY_SCALE,

                                            RoundingMode.HALF_UP

                                    )

                                    .min(paymentLeft);



            applyLinePayment(item, linePayment);



            paymentLeft = paymentLeft.subtract(linePayment);

        }

    }

    private BigDecimal paymentDistributionWeight(

            FinancialDocumentItem item

    ) {

        BigDecimal netAmount = money(item.getNetAmount());

        if (netAmount.signum() > 0) {

            return netAmount;

        }



        return money(item.getPatientShareAmount());

    }

    private void applyLinePayment(

            FinancialDocumentItem item,

            BigDecimal linePayment

    ) {

        if (linePayment.signum() <= 0) {

            return;

        }



        BigDecimal newPaid =

                money(item.getPaidAmount()).add(linePayment);

        BigDecimal patientShare = money(item.getPatientShareAmount());

        BigDecimal newRemaining =

                patientShare

                        .subtract(newPaid)

                        .max(BigDecimal.ZERO);



        item.setPaidAmount(newPaid);

        item.setRemainingAmount(newRemaining);

        item.setStatus(

                resolveStatus(

                        newPaid,

                        patientShare,

                        newRemaining

                )

        );

    }

    private FinancialDocumentItemStatus resolveStatus(

            BigDecimal paidAmount,

            BigDecimal patientShare,

            BigDecimal remaining

    ) {

        if (remaining.signum() <= 0 && patientShare.signum() > 0) {

            return FinancialDocumentItemStatus.PAID;

        }



        if (paidAmount.signum() > 0) {

            return FinancialDocumentItemStatus.PARTIALLY_PAID;

        }



        return FinancialDocumentItemStatus.PENDING;

    }



    private FinancialDocument loadInvoice(Long invoiceId) {

        FinancialDocument invoice =

                financialDocumentRepository

                        .findById(invoiceId)

                        .orElseThrow(() ->

                                new NotFoundAlertException(

                                        "Invoice not found with id " + invoiceId,

                                        ENTITY_NAME,

                                        "invoice.notFound"

                                )

                        );



        if (invoice.getDocumentType() != FinancialDocumentType.INVOICE) {

            throw new BadRequestAlertException(

                    "Financial document is not an invoice.",

                    ENTITY_NAME,

                    "invoice.invalidType"

            );

        }



        return invoice;

    }



    private BigDecimal money(BigDecimal value) {

        if (value == null) {

            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        }



        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    }

}


