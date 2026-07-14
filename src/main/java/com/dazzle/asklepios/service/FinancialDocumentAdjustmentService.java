package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.PatientLedgerEntry;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.LedgerAccount;
import com.dazzle.asklepios.domain.enumeration.LedgerEntryType;
import com.dazzle.asklepios.domain.enumeration.LedgerSource;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class FinancialDocumentAdjustmentService {

    private final FinancialDocumentRepository documentRepo;
    private final PatientLedgerRepository ledgerRepository;
    private final FinancialDocumentStatusService statusService;
    private final FinancialDocumentBalanceService balanceService;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public FinancialDocument createCreditNote(
            Long invoiceId,
            BigDecimal amount,
            String reason
    ) {

        FinancialDocument invoice = documentRepo.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice not found"));

        // ✅ 1. load invoice


// ✅ ensure it's invoice
        if (!FinancialDocumentType.INVOICE.equals(invoice.getDocumentType())) {
            throw new IllegalStateException("Credit note must reference an invoice");
        }

// ✅ ensure invoice is issued or later 💣
        if (invoice.getStatus() == FinancialDocumentStatus.DRAFT) {
            throw new IllegalStateException("Cannot create Credit Note before invoice is ISSUED");
        }

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid credit amount");
        }

        if (amount.compareTo(invoice.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Credit exceeds invoice amount");
        }

        // ✅ 2. create Credit Note
        FinancialDocument creditNote = FinancialDocument.builder()
                .documentType(FinancialDocumentType.CREDIT_NOTE)
                .parentDocumentId(invoiceId)
                .patientId(invoice.getPatientId())
                .encounterId(invoice.getEncounterId())
                .totalAmount(amount)
                .currency(invoice.getCurrency())
                .status(FinancialDocumentStatus.ISSUED)
                .createdDate(Instant.now())
                .build();

        FinancialDocument saved = documentRepo.save(creditNote);

        // ✅ 3. Ledger impact 💣
        applyCreditNoteLedger(saved);

        // ✅ 4. update invoice status (auto)
        updateInvoiceStatus(invoiceId);

        return saved;
    }

    // ==============================
    // ✅ LEDGER FOR CREDIT NOTE 💣
    // ==============================
    private void applyCreditNoteLedger(FinancialDocument creditNote) {

        Long patientId = creditNote.getPatientId();
        BigDecimal amount = creditNote.getTotalAmount();

        // ✅ DR Revenue (reduce revenue)
        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.DEBIT)
                        .account(LedgerAccount.REVENUE)
                        .source(LedgerSource.CREDIT_NOTE)
                        .referenceId(creditNote.getId())
                        .amount(amount)
                        .currency(creditNote.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );

        // ✅ CR Receivable (reduce debt)
        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.CREDIT)
                        .account(LedgerAccount.PATIENT_RECEIVABLE)
                        .source(LedgerSource.CREDIT_NOTE)
                        .referenceId(creditNote.getId())
                        .amount(amount)
                        .currency(creditNote.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );
    }

    private void updateInvoiceStatus(Long invoiceId) {

        FinancialDocument invoice = documentRepo.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice not found"));

        invoice.setStatus(statusService.calculate(invoiceId));

        documentRepo.save(invoice);
    }

    // ==============================
// ✅ CREATE DEBIT NOTE 💣
// ==============================
    public FinancialDocument createDebitNote(
            Long invoiceId,
            BigDecimal amount,
            String reason
    ) {

        // ✅ 1. load invoice
        FinancialDocument invoice = documentRepo.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice not found"));

        // ✅ VALIDATION 💣
        if (!FinancialDocumentType.INVOICE.equals(invoice.getDocumentType())) {
            throw new IllegalStateException("Debit note must reference an invoice");
        }

        if (invoice.getStatus() == FinancialDocumentStatus.DRAFT) {
            throw new IllegalStateException("Cannot create Debit Note before invoice is ISSUED");
        }

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid debit amount");
        }

        // ✅ 2. create Debit Note
        FinancialDocument debitNote = FinancialDocument.builder()
                .documentType(FinancialDocumentType.DEBIT_NOTE)
                .parentDocumentId(invoiceId)
                .patientId(invoice.getPatientId())
                .encounterId(invoice.getEncounterId())
                .totalAmount(amount)
                .currency(invoice.getCurrency())
                .status(FinancialDocumentStatus.ISSUED)
                .createdDate(Instant.now())
                .build();

        FinancialDocument saved = documentRepo.save(debitNote);

        // ✅ 3. Ledger impact 💣
        applyDebitNoteLedger(saved);

        // ✅ 4. update invoice status
        updateInvoiceStatus(invoiceId);

        return saved;
    }
    // ==============================
// ✅ LEDGER FOR DEBIT NOTE 💣
// ==============================
    private void applyDebitNoteLedger(FinancialDocument debitNote) {

        Long patientId = debitNote.getPatientId();
        BigDecimal amount = debitNote.getTotalAmount();

        // ✅ DR Receivable (increase debt)
        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.DEBIT)
                        .account(LedgerAccount.PATIENT_RECEIVABLE)
                        .source(LedgerSource.DEBIT_NOTE)
                        .referenceId(debitNote.getId())
                        .amount(amount)
                        .currency(debitNote.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );

        // ✅ CR Revenue (increase revenue)
        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.CREDIT)
                        .account(LedgerAccount.REVENUE)
                        .source(LedgerSource.DEBIT_NOTE)
                        .referenceId(debitNote.getId())
                        .amount(amount)
                        .currency(debitNote.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );
    }

    // ==============================
// ✅ CREATE REFUND 💣
// ==============================
    public void createRefund(
            Long invoiceId,
            BigDecimal amount
    ) {

        // ✅ 1. load invoice
        FinancialDocument invoice = documentRepo.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice not found"));

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid refund amount");
        }

        // ✅ 2. calculate outstanding
        BigDecimal balance = balanceService.calculateOutstanding(invoiceId);

        if (balance.compareTo(ZERO) >= 0) {
            throw new IllegalStateException("No overpayment -> no refund allowed");
        }

        BigDecimal overpayment = balance.abs();

        if (amount.compareTo(overpayment) > 0) {
            throw new IllegalStateException("Refund exceeds overpayment");
        }

        // ✅ 3. Ledger impact 💣 (REFUND)
        applyRefundLedger(invoice, amount);
    }

    // ==============================
// ✅ LEDGER FOR REFUND 💣
// ==============================
    private void applyRefundLedger(
            FinancialDocument invoice,
            BigDecimal amount
    ) {

        Long patientId = invoice.getPatientId();

        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.DEBIT)
                        .account(LedgerAccount.PATIENT_RECEIVABLE)
                        .source(LedgerSource.REFUND)
                        .referenceId(invoice.getId())
                        .amount(amount)
                        .currency(invoice.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );

        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.CREDIT)
                        .account(LedgerAccount.CASH)
                        .source(LedgerSource.REFUND)
                        .referenceId(invoice.getId())
                        .amount(amount)
                        .currency(invoice.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );
    }

    private BigDecimal getOverpayment(Long invoiceId) {

        BigDecimal balance = balanceService.calculateOutstanding(invoiceId);

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            return balance.abs(); // ✅ overpayment
        }

        return BigDecimal.ZERO;
    }

}