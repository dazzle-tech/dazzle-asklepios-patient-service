package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.CatalogItemPricingPreviewService;
import com.dazzle.asklepios.service.FinancialDocumentAdjustmentService;
import com.dazzle.asklepios.service.InvoiceBalancePaymentService;
import com.dazzle.asklepios.service.InvoicePricingSummaryService;
import com.dazzle.asklepios.service.dto.billing.AddableChargeLineResponse;
import com.dazzle.asklepios.service.dto.billing.CollectInvoiceBalanceRequest;
import com.dazzle.asklepios.service.dto.billing.CollectInvoiceBalanceResult;
import com.dazzle.asklepios.service.dto.billing.SyncInvoicePaymentsResult;
import com.dazzle.asklepios.service.dto.billing.CreateFinancialDocumentAdjustmentRequest;
import com.dazzle.asklepios.service.dto.billing.FinancialDocumentAdjustmentResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceAdjustmentSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceLineItemResponse;
import com.dazzle.asklepios.service.dto.billing.InvoicePricingSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingRequest;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingResult;
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
    private final InvoicePricingSummaryService invoicePricingSummaryService;
    private final InvoiceBalancePaymentService invoiceBalancePaymentService;
    private final CatalogItemPricingPreviewService catalogItemPricingPreviewService;

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

    @GetMapping("/financial-documents/{invoiceId}/pricing-summary")
    public InvoicePricingSummaryResponse getPricingSummary(
            @PathVariable Long invoiceId
    ) {
        return invoicePricingSummaryService.getByInvoiceId(invoiceId);
    }

    @PostMapping("/financial-documents/preview-catalog-item-pricing")
    public PreviewCatalogItemPricingResult previewCatalogItemPricing(
            @Valid @RequestBody PreviewCatalogItemPricingRequest request
    ) {
        return catalogItemPricingPreviewService.preview(request);
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

    @PostMapping("/financial-documents/{invoiceId}/collect-balance")
    public CollectInvoiceBalanceResult collectInvoiceBalance(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CollectInvoiceBalanceRequest request
    ) {
        return invoiceBalancePaymentService.collectBalance(invoiceId, request);
    }

    @PostMapping("/financial-documents/{invoiceId}/sync-payments")
    public SyncInvoicePaymentsResult syncInvoicePayments(
            @PathVariable Long invoiceId
    ) {
        return invoiceBalancePaymentService.syncChargePayments(invoiceId);
    }
}
