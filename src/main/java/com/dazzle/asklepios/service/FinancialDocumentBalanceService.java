package com.dazzle.asklepios.service;



import com.dazzle.asklepios.domain.FinancialDocument;

import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;

import com.dazzle.asklepios.domain.FinancialDocumentItem;

import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;

import com.dazzle.asklepios.repository.FinancialDocumentRepository;

import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;



import java.math.BigDecimal;

import java.util.List;



@Service

@RequiredArgsConstructor

public class FinancialDocumentBalanceService {



    private static final Logger LOG =

            LoggerFactory.getLogger(FinancialDocumentBalanceService.class);



    private final FinancialDocumentRepository documentRepo;

    private final FinancialDocumentItemRepository itemRepo;

    private final PatientPaymentAllocationRepository allocationRepo;



    private static final BigDecimal ZERO = BigDecimal.ZERO;



    public BigDecimal calculateOutstanding(Long documentId) {



        FinancialDocument invoice =

                documentRepo.findById(documentId)

                        .orElseThrow(() -> new IllegalStateException("Document not found"));



        BigDecimal invoiceTotal = safe(invoice.getTotalAmount());



        List<FinancialDocument> children =

                documentRepo.findAllByParentDocumentId(documentId);



        BigDecimal totalCreditNotes = children.stream()

                .filter(d -> d.getDocumentType() == FinancialDocumentType.CREDIT_NOTE)

                .map(d -> safe(d.getTotalAmount()))

                .reduce(ZERO, BigDecimal::add);



        BigDecimal totalDebitNotes = children.stream()

                .filter(d -> d.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)

                .map(d -> safe(d.getTotalAmount()))

                .reduce(ZERO, BigDecimal::add);



        BigDecimal invoiceItemPaid =

                itemRepo.findByDocument_Id(documentId)

                        .stream()

                        .map(FinancialDocumentItem::getPaidAmount)

                        .map(this::safe)

                        .reduce(ZERO, BigDecimal::add);



        BigDecimal debitNoteItemPaid =

                children.stream()

                        .filter(d -> d.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)

                        .flatMap(d -> itemRepo.findByDocument_Id(d.getId()).stream())

                        .map(FinancialDocumentItem::getPaidAmount)

                        .map(this::safe)

                        .reduce(ZERO, BigDecimal::add);



        BigDecimal itemPaid = invoiceItemPaid.add(debitNoteItemPaid);



        BigDecimal allocationPaid =

                safe(allocationRepo.sumPaidByDocument(documentId));



        BigDecimal totalPaid = itemPaid.max(allocationPaid);



        BigDecimal formulaOutstanding =
                invoiceTotal
                        .add(totalDebitNotes)
                        .subtract(totalCreditNotes)
                        .subtract(safe(totalPaid))
                        .max(ZERO);

        if (invoice.getDocumentType() != FinancialDocumentType.INVOICE) {
            return formulaOutstanding;
        }

        BigDecimal lineOutstanding =
                sumInvoiceLineDerivedRemaining(documentId, children);

        if (lineOutstanding.compareTo(formulaOutstanding) != 0) {
            LOG.debug(
                    "[INVOICE_BALANCE] outstanding mismatch documentId={} line={} formula={} "
                            + "debitNotes={} creditNotes={}",
                    documentId,
                    lineOutstanding,
                    formulaOutstanding,
                    totalDebitNotes,
                    totalCreditNotes
            );
        }

        return resolveInvoiceOutstanding(
                lineOutstanding,
                formulaOutstanding,
                totalDebitNotes,
                totalCreditNotes
        );
    }

    /**
     * Reconciles line-level remaining with the invoice header formula without
     * double-counting debit notes or ignoring credit-note reductions.
     */
    private BigDecimal resolveInvoiceOutstanding(
            BigDecimal lineOutstanding,
            BigDecimal formulaOutstanding,
            BigDecimal totalDebitNotes,
            BigDecimal totalCreditNotes
    ) {
        if (lineOutstanding.compareTo(formulaOutstanding) == 0) {
            return lineOutstanding;
        }

        if (totalCreditNotes.signum() > 0
                && formulaOutstanding.compareTo(lineOutstanding) < 0) {
            return formulaOutstanding;
        }

        if (lineOutstanding.compareTo(formulaOutstanding) < 0) {
            if (totalDebitNotes.signum() > 0
                    && formulaOutstanding.compareTo(lineOutstanding) > 0) {
                return formulaOutstanding;
            }

            return lineOutstanding;
        }

        if (formulaOutstanding.compareTo(lineOutstanding) < 0) {
            return formulaOutstanding;
        }

        return lineOutstanding;

    }



    /**

     * Invoice header total plus debit notes minus credit notes.

     */

    public BigDecimal calculateAdjustedTotal(Long documentId) {

        FinancialDocument invoice =

                documentRepo.findById(documentId)

                        .orElseThrow(() -> new IllegalStateException("Document not found"));



        List<FinancialDocument> children =

                documentRepo.findAllByParentDocumentId(documentId);



        BigDecimal totalCreditNotes = children.stream()

                .filter(d -> d.getDocumentType() == FinancialDocumentType.CREDIT_NOTE)

                .map(d -> safe(d.getTotalAmount()))

                .reduce(ZERO, BigDecimal::add);



        BigDecimal totalDebitNotes = children.stream()

                .filter(d -> d.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)

                .map(d -> safe(d.getTotalAmount()))

                .reduce(ZERO, BigDecimal::add);



        return safe(invoice.getTotalAmount())

                .add(totalDebitNotes)

                .subtract(totalCreditNotes);

    }



    public BigDecimal calculatePaidAmount(Long documentId) {

        FinancialDocument document =

                documentRepo.findById(documentId)

                        .orElseThrow(() -> new IllegalStateException("Document not found"));



        if (document.getDocumentType() == FinancialDocumentType.INVOICE) {

            return sumInvoiceLinePaid(documentId);

        }



        return calculateAdjustedTotal(documentId)

                .subtract(calculateOutstanding(documentId))

                .max(ZERO);

    }



    private BigDecimal sumInvoiceLinePaid(Long documentId) {

        List<FinancialDocument> children =

                documentRepo.findAllByParentDocumentId(documentId);



        BigDecimal invoiceItemPaid =

                itemRepo.findByDocument_Id(documentId)

                        .stream()

                        .map(FinancialDocumentItem::getPaidAmount)

                        .map(this::safe)

                        .reduce(ZERO, BigDecimal::add);



        BigDecimal debitNoteItemPaid =

                children.stream()

                        .filter(d -> d.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)

                        .flatMap(d -> itemRepo.findByDocument_Id(d.getId()).stream())

                        .map(FinancialDocumentItem::getPaidAmount)

                        .map(this::safe)

                        .reduce(ZERO, BigDecimal::add);



        BigDecimal itemPaid = invoiceItemPaid.add(debitNoteItemPaid);

        BigDecimal allocationPaid = safe(allocationRepo.sumPaidByDocument(documentId));



        return itemPaid.max(allocationPaid);

    }



    private BigDecimal sumInvoiceLineDerivedRemaining(

            Long invoiceId,

            List<FinancialDocument> children

    ) {

        BigDecimal invoiceRemaining =

                itemRepo.findByDocument_Id(invoiceId)

                        .stream()

                        .map(this::deriveItemRemaining)

                        .reduce(ZERO, BigDecimal::add);



        BigDecimal debitNoteRemaining =

                children.stream()

                        .filter(d -> d.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)

                        .flatMap(d -> itemRepo.findByDocument_Id(d.getId()).stream())

                        .map(this::deriveItemRemaining)

                        .reduce(ZERO, BigDecimal::add);



        return invoiceRemaining.add(debitNoteRemaining);

    }



    private BigDecimal deriveItemRemaining(FinancialDocumentItem item) {

        BigDecimal storedRemaining = safe(item.getRemainingAmount());

        if (storedRemaining.signum() > 0) {

            return storedRemaining;

        }



        BigDecimal paid = safe(item.getPaidAmount());

        if (paid.signum() > 0) {

            return ZERO;

        }



        BigDecimal collectible = safe(item.getPatientShareAmount());

        if (collectible.signum() <= 0) {

            collectible = safe(item.getNetAmount());

        }



        return collectible.subtract(paid).max(ZERO);

    }



    private BigDecimal safe(BigDecimal v) {

        return v == null ? ZERO : v;

    }

}


