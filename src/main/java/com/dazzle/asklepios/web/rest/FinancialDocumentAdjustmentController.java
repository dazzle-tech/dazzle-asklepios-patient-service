package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.service.FinancialDocumentAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class FinancialDocumentAdjustmentController {

    private final FinancialDocumentAdjustmentService adjustmentService;

    @PostMapping("/financial-documents/{invoiceId}/credit-note")
    public FinancialDocument createCreditNote(
            @PathVariable Long invoiceId,
            @RequestParam BigDecimal amount,
            @RequestParam String reason
    ) {
        return adjustmentService.createCreditNote(invoiceId, amount, reason);
    }

    @PostMapping("/financial-documents/{invoiceId}/debit-note")
    public FinancialDocument createDebitNote(
            @PathVariable Long invoiceId,
            @RequestParam BigDecimal amount,
            @RequestParam String reason
    ) {
        return adjustmentService.createDebitNote(invoiceId, amount, reason);
    }

    @PostMapping("/financial-documents/{invoiceId}/refund")
    public void createRefund(
            @PathVariable Long invoiceId,
            @RequestParam BigDecimal amount
    ) {
        adjustmentService.createRefund(invoiceId, amount);
    }





}
