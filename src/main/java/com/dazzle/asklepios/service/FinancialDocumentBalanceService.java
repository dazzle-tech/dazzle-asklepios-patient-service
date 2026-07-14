package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialDocumentBalanceService {

    private final FinancialDocumentRepository documentRepo;
    private final PatientPaymentAllocationRepository allocationRepo;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public BigDecimal calculateOutstanding(Long documentId) {

        FinancialDocument invoice =
                documentRepo.findById(documentId)
                        .orElseThrow(() -> new IllegalStateException("Document not found"));

        // ✅ 1. Invoice Base
        BigDecimal invoiceTotal = safe(invoice.getTotalAmount());

        // ✅ 2. Load adjustments (CN / DN)
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

        // ✅ 3. Payments (from allocations)
        BigDecimal totalPaid =
                allocationRepo.sumPaidByDocument(documentId);

        // ✅ FINAL FORMULA 💣
        return invoiceTotal
                .add(totalDebitNotes)
                .subtract(totalCreditNotes)
                .subtract(safe(totalPaid));
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}