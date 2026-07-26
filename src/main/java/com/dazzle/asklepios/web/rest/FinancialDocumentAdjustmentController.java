package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.FinancialDocumentAdjustmentService;
import com.dazzle.asklepios.service.dto.billing.AddableChargeLineResponse;
import com.dazzle.asklepios.service.dto.billing.CreateFinancialDocumentAdjustmentRequest;
import com.dazzle.asklepios.service.dto.billing.FinancialDocumentAdjustmentResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceAdjustmentSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceLineItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class FinancialDocumentAdjustmentController {

    private final FinancialDocumentAdjustmentService adjustmentService;

    @GetMapping("/financial-documents/{invoiceId}/items")
    public List<InvoiceLineItemResponse> listInvoiceItems(@PathVariable Long invoiceId) {
        return adjustmentService.listInvoiceLineItems(invoiceId);
    }

    @GetMapping("/financial-documents/{invoiceId}/addable-charge-lines")
    public List<AddableChargeLineResponse> listAddableChargeLines(@PathVariable Long invoiceId) {
        return adjustmentService.listAddableChargeLines(invoiceId);
    }

    @GetMapping("/financial-documents/{invoiceId}/adjustments")
    public InvoiceAdjustmentSummaryResponse getAdjustments(@PathVariable Long invoiceId) {
        return adjustmentService.getInvoiceAdjustmentSummary(invoiceId);
    }

    @PostMapping("/financial-documents/{invoiceId}/credit-note")
    public FinancialDocumentAdjustmentResponse createCreditNote(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CreateFinancialDocumentAdjustmentRequest request
    ) {
        return adjustmentService.createCreditNote(invoiceId, request);
    }

    @PostMapping("/financial-documents/{invoiceId}/debit-note")
    public FinancialDocumentAdjustmentResponse createDebitNote(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CreateFinancialDocumentAdjustmentRequest request
    ) {
        return adjustmentService.createDebitNote(invoiceId, request);
    }

    @PostMapping("/financial-documents/{invoiceId}/refund")
    public void createRefund(
            @PathVariable Long invoiceId,
            @RequestParam BigDecimal amount
    ) {
        adjustmentService.createRefund(invoiceId, amount);
    }
}
