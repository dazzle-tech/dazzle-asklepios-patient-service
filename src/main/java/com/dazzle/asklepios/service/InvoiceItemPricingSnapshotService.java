package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.BillingAdjustmentResolveResponse;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingPricingSnapshotRepository;
import com.dazzle.asklepios.service.dto.billing.InvoiceItemPricingAdjustmentSnapshot;
import com.dazzle.asklepios.service.dto.billing.InvoiceItemPricingAdjustmentSnapshot.AppliedDiscountEntry;
import com.dazzle.asklepios.service.dto.billing.InvoiceItemPricingAdjustmentSnapshot.AppliedTaxEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceItemPricingSnapshotService {

    private static final int MONEY_SCALE = 4;

    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingPricingSnapshotRepository billingPricingSnapshotRepository;
    private final ObjectMapper objectMapper;

    public void captureChargeLineSnapshots(List<FinancialDocumentItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<Long, BillingChargeLine> chargeLinesById = new HashMap<>();

        for (FinancialDocumentItem item : items) {
            Long chargeLineId = item.getBillingChargeLineId();
            if (chargeLineId == null) {
                continue;
            }

            BillingChargeLine chargeLine =
                    chargeLinesById.computeIfAbsent(
                            chargeLineId,
                            id ->
                                    billingChargeLineRepository
                                            .findById(id)
                                            .orElse(null)
                    );

            if (chargeLine == null) {
                continue;
            }

            InvoiceItemPricingAdjustmentSnapshot snapshot =
                    readSnapshot(item);

            snapshot = appendChargeLinePricing(snapshot, chargeLine);
            writeSnapshot(item, snapshot);
        }
    }

    public void appendInvoiceScopeAdjustments(
            List<FinancialDocumentItem> items,
            TaxApplicableOn taxApplicableOn,
            DiscountApplicableOn discountApplicableOn,
            BillingAdjustmentResolveResponse rules,
            BigDecimal invoiceDiscount,
            BigDecimal invoiceTax
    ) {
        if (items == null || items.isEmpty() || rules == null) {
            return;
        }

        BigDecimal subtotal =
                items.stream()
                        .map(FinancialDocumentItem::getNetAmount)
                        .map(this::money)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (subtotal.signum() <= 0) {
            return;
        }

        BigDecimal allocatedDiscount = BigDecimal.ZERO;
        BigDecimal allocatedTax = BigDecimal.ZERO;

        for (int index = 0; index < items.size(); index++) {
            FinancialDocumentItem item = items.get(index);
            BigDecimal lineNet = money(item.getNetAmount());
            BigDecimal weight =
                    lineNet.divide(
                            subtotal,
                            MONEY_SCALE,
                            RoundingMode.HALF_UP
                    );
            boolean isLast = index == items.size() - 1;

            BigDecimal lineDiscountShare =
                    isLast
                            ? invoiceDiscount.subtract(allocatedDiscount)
                            : invoiceDiscount
                                    .multiply(weight)
                                    .setScale(
                                            MONEY_SCALE,
                                            RoundingMode.HALF_UP
                                    );

            BigDecimal lineTaxShare =
                    isLast
                            ? invoiceTax.subtract(allocatedTax)
                            : invoiceTax
                                    .multiply(weight)
                                    .setScale(
                                            MONEY_SCALE,
                                            RoundingMode.HALF_UP
                                    );

            InvoiceItemPricingAdjustmentSnapshot snapshot =
                    readSnapshot(item);

            if (lineDiscountShare.signum() > 0 && hasDiscountRule(rules)) {
                snapshot =
                        snapshot.withDiscount(
                                new AppliedDiscountEntry(
                                        "INVOICE_RULE",
                                        rules.discountId(),
                                        rules.discountCode(),
                                        rules.discountName(),
                                        discountApplicableOn,
                                        rules.discountType(),
                                        rules.discountRate(),
                                        rules.discountFixedAmount(),
                                        lineDiscountShare
                                )
                        );
            }

            if (lineTaxShare.signum() > 0 && hasTaxRule(rules)) {
                snapshot =
                        snapshot.withTax(
                                new AppliedTaxEntry(
                                        "INVOICE_RULE",
                                        rules.taxId(),
                                        rules.taxCode(),
                                        rules.taxName(),
                                        taxApplicableOn,
                                        rules.taxType(),
                                        rules.taxCalculationType(),
                                        rules.taxRate(),
                                        rules.taxFixedAmount(),
                                        lineTaxShare
                                )
                        );
            }

            writeSnapshot(item, snapshot);
            allocatedDiscount = allocatedDiscount.add(lineDiscountShare);
            allocatedTax = allocatedTax.add(lineTaxShare);
        }
    }

    public void appendLineScopeAdjustments(
            FinancialDocumentItem item,
            BillingAdjustmentResolveResponse rules,
            BigDecimal discountAmount,
            BigDecimal taxAmount
    ) {
        if (item == null || rules == null) {
            return;
        }

        InvoiceItemPricingAdjustmentSnapshot snapshot = readSnapshot(item);

        if (discountAmount.signum() > 0 && hasDiscountRule(rules)) {
            snapshot =
                    snapshot.withDiscount(
                            new AppliedDiscountEntry(
                                    "INVOICE_RULE",
                                    rules.discountId(),
                                    rules.discountCode(),
                                    rules.discountName(),
                                    rules.discountApplicableOn(),
                                    rules.discountType(),
                                    rules.discountRate(),
                                    rules.discountFixedAmount(),
                                    discountAmount
                            )
                    );
        }

        if (taxAmount.signum() > 0 && hasTaxRule(rules)) {
            snapshot =
                    snapshot.withTax(
                            new AppliedTaxEntry(
                                    "INVOICE_RULE",
                                    rules.taxId(),
                                    rules.taxCode(),
                                    rules.taxName(),
                                    rules.taxApplicableOn(),
                                    rules.taxType(),
                                    rules.taxCalculationType(),
                                    rules.taxRate(),
                                    rules.taxFixedAmount(),
                                    taxAmount
                            )
                    );
        }

        writeSnapshot(item, snapshot);
    }

    public InvoiceItemPricingAdjustmentSnapshot readSnapshot(
            FinancialDocumentItem item
    ) {
        if (item.getPricingAdjustmentSnapshot() == null) {
            return InvoiceItemPricingAdjustmentSnapshot.empty();
        }

        return objectMapper.convertValue(
                item.getPricingAdjustmentSnapshot(),
                InvoiceItemPricingAdjustmentSnapshot.class
        );
    }

    private InvoiceItemPricingAdjustmentSnapshot appendChargeLinePricing(
            InvoiceItemPricingAdjustmentSnapshot snapshot,
            BillingChargeLine chargeLine
    ) {
        BillingPricingSnapshot pricingSnapshot =
                billingPricingSnapshotRepository
                        .findFirstByChargeLine_IdAndStatusOrderByIdDesc(
                                chargeLine.getId(),
                                com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus.ACTIVE
                        )
                        .orElse(null);

        if (pricingSnapshot == null && chargeLine.getCurrentPricingSnapshotId() != null) {
            pricingSnapshot =
                    billingPricingSnapshotRepository
                            .findById(chargeLine.getCurrentPricingSnapshotId())
                            .orElse(null);
        }

        if (pricingSnapshot == null) {
            return snapshot;
        }

        InvoiceItemPricingAdjustmentSnapshot updated = snapshot;

        if (money(pricingSnapshot.getDiscountAmount()).signum() > 0) {
            updated =
                    updated.withDiscount(
                            new AppliedDiscountEntry(
                                    pricingSnapshot.getPriceListId() != null
                                            ? "PRICE_LIST"
                                            : "SERVICE",
                                    pricingSnapshot.getDiscountId(),
                                    pricingSnapshot.getPriceListItemCode(),
                                    firstNonBlank(
                                            pricingSnapshot.getDiscountReason(),
                                            pricingSnapshot.getPriceListName(),
                                            "Charge discount"
                                    ),
                                    null,
                                    pricingSnapshot.getDiscountType(),
                                    pricingSnapshot.getDiscountRate(),
                                    null,
                                    money(pricingSnapshot.getDiscountAmount())
                            )
                    );
        }

        if (money(pricingSnapshot.getTaxAmount()).signum() > 0) {
            updated =
                    updated.withTax(
                            new AppliedTaxEntry(
                                    "SERVICE",
                                    pricingSnapshot.getTaxId(),
                                    null,
                                    "Charge tax",
                                    null,
                                    pricingSnapshot.getTaxType(),
                                    null,
                                    pricingSnapshot.getTaxRate(),
                                    null,
                                    money(pricingSnapshot.getTaxAmount())
                            )
                    );
        }

        return updated;
    }

    private void writeSnapshot(
            FinancialDocumentItem item,
            InvoiceItemPricingAdjustmentSnapshot snapshot
    ) {
        item.setPricingAdjustmentSnapshot(
                objectMapper.valueToTree(snapshot)
        );
    }

    private boolean hasDiscountRule(BillingAdjustmentResolveResponse rules) {
        return rules.discountId() != null && rules.discountType() != null;
    }

    private boolean hasTaxRule(BillingAdjustmentResolveResponse rules) {
        return rules.taxId() != null
                && rules.taxType() != null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
