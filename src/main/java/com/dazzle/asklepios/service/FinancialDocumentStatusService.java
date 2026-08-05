package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;

@Service
@RequiredArgsConstructor
public class FinancialDocumentStatusService {

    private final FinancialDocumentItemRepository itemRepo;
    private final PatientPaymentAllocationRepository allocationRepo;
    private final FinancialDocumentRepository documentRepo;
    private final FinancialDocumentBalanceService balanceService;
    public FinancialDocumentStatus calculate(Long documentId) {

        FinancialDocument document =
                documentRepo.findById(documentId)
                        .orElseThrow(() -> new IllegalStateException("Document not found"));

        BigDecimal totalInvoice = safe(document.getTotalAmount());

        BigDecimal balance = balanceService.calculateOutstanding(documentId);

        if (balance.compareTo(BigDecimal.ZERO) == 0) {
            return FinancialDocumentStatus.PAID;
        }

        if (balance.compareTo(totalInvoice) < 0) {
            return FinancialDocumentStatus.PARTIALLY_PAID;
        }

        return FinancialDocumentStatus.ISSUED;
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}
