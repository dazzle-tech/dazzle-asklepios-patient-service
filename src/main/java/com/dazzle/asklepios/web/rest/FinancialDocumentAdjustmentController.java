package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.CatalogItemPricingPreviewService;
import com.dazzle.asklepios.service.FinancialDocumentAdjustmentService;
import com.dazzle.asklepios.service.InvoiceBalancePaymentService;
import com.dazzle.asklepios.service.InvoicePricingSummaryService;
import com.dazzle.asklepios.service.dto.billing.AddableChargeLineResponse;
import com.dazzle.asklepios.service.dto.billing.CollectInvoiceBalanceRequest;
import com.dazzle.asklepios.service.dto.billing.CollectInvoiceBalanceResult;
import com.dazzle.asklepios.service.dto.billing.SyncInvoicePaymentsResult;
import com.dazzle.asklepios.service.dto.billing.CreateDiscountCreditNoteRequest;
import com.dazzle.asklepios.service.dto.billing.CreateFinancialDocumentAdjustmentRequest;
import com.dazzle.asklepios.service.dto.billing.DiscountCreditNotePreviewResponse;
import com.dazzle.asklepios.service.dto.billing.FinancialDocumentAdjustmentResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceAdjustmentSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceLineItemResponse;
import com.dazzle.asklepios.service.dto.billing.InvoicePricingSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingRequest;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Logger LOG =
            LoggerFactory.getLogger(FinancialDocumentAdjustmentController.class);

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

    @PostMapping("/financial-documents/{invoiceId}/discount-credit-note/preview")
    public DiscountCreditNotePreviewResponse previewDiscountCreditNote(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CreateDiscountCreditNoteRequest request
    ) {
        return adjustmentService.previewDiscountCreditNote(invoiceId, request);
    }

    @PostMapping("/financial-documents/{invoiceId}/discount-credit-note")
    public FinancialDocumentAdjustmentResponse createDiscountCreditNote(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CreateDiscountCreditNoteRequest request
    ) {
        try {
            return adjustmentService.createDiscountCreditNote(invoiceId, request);
        } catch (DataIntegrityViolationException ex) {
            throw translateAdjustmentDataIntegrityViolation(ex, "discountCreditNote");
        }
    }

    @PostMapping("/financial-documents/{invoiceId}/credit-note")
    public FinancialDocumentAdjustmentResponse createCreditNote(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CreateFinancialDocumentAdjustmentRequest request
    ) {
        try {
            return adjustmentService.createCreditNote(invoiceId, request);
        } catch (DataIntegrityViolationException ex) {
            throw translateAdjustmentDataIntegrityViolation(ex, "creditNote");
        }
    }

    @PostMapping("/financial-documents/{invoiceId}/debit-note")
    public FinancialDocumentAdjustmentResponse createDebitNote(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CreateFinancialDocumentAdjustmentRequest request
    ) {
        try {
            return adjustmentService.createDebitNote(invoiceId, request);
        } catch (DataIntegrityViolationException ex) {
            throw translateAdjustmentDataIntegrityViolation(ex, "debitNote");
        }
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

    /**
     * Billing invariants are enforced by database check constraints, so a broken
     * adjustment must surface as a readable 400 instead of a 500.
     */
    private BadRequestAlertException translateAdjustmentDataIntegrityViolation(
            DataIntegrityViolationException ex,
            String errorKeyPrefix
    ) {
        String message = ex.getMostSpecificCause().getMessage();
        String normalized = message == null ? "" : message.toLowerCase();

        if (normalized.contains("ck_billing_charge_line_allocation_balance")) {
            return new BadRequestAlertException(
                    "Cannot apply this adjustment because the collected or reserved amount "
                            + "on the charge line no longer matches the remaining balance. "
                            + "Refund or unallocate the extra payment first, then retry.",
                    "financialDocumentAdjustment",
                    errorKeyPrefix + ".allocationBalance"
            );
        }

        if (normalized.contains("ck_billing_charge_line_responsibility")) {
            return new BadRequestAlertException(
                    "Cannot apply this adjustment because patient and insurance shares "
                            + "do not add up to the line net amount.",
                    "financialDocumentAdjustment",
                    errorKeyPrefix + ".responsibilityBalance"
            );
        }

        if (normalized.contains("ck_billing_charge_line")
                || normalized.contains("ck_financial_document")) {
            return new BadRequestAlertException(
                    "Cannot apply this adjustment because it would leave the billing amounts "
                            + "inconsistent. Please review the quantity and price and retry.",
                    "financialDocumentAdjustment",
                    errorKeyPrefix + ".amountsInconsistent"
            );
        }

        LOG.error(
                "Unmapped data integrity violation while creating {}",
                errorKeyPrefix,
                ex
        );

        return new BadRequestAlertException(
                "Cannot apply this adjustment because it violates a billing data rule.",
                "financialDocumentAdjustment",
                errorKeyPrefix + ".dataIntegrity"
        );
    }
}
