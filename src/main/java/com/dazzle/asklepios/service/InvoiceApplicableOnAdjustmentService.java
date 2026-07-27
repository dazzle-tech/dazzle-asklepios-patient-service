package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.SetupBillingAdjustmentClient;
import com.dazzle.asklepios.client.setup.dto.BillingAdjustmentResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingAdjustmentResolveResponse;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.TaxCalculationType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceApplicableOnAdjustmentService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    InvoiceApplicableOnAdjustmentService.class
            );

    private static final int MONEY_SCALE = 4;

    private final SetupBillingAdjustmentClient
            setupBillingAdjustmentClient;

    private final InvoiceItemPricingSnapshotService
            invoiceItemPricingSnapshotService;

    public List<FinancialDocumentItem> applyApplicableOnAdjustments(
            List<FinancialDocumentItem> items,
            Long facilityId,
            Currency currency,
            LocalDate pricingDate
    ) {
        if (items == null || items.isEmpty()) {
            return items;
        }

        applyScopeAdjustments(
                items,
                facilityId,
                currency,
                pricingDate,
                TaxApplicableOn.INVOICE_LINE,
                DiscountApplicableOn.INVOICE_LINE
        );

        applyScopeAdjustments(
                items,
                facilityId,
                currency,
                pricingDate,
                TaxApplicableOn.INVOICE,
                DiscountApplicableOn.INVOICE
        );

        return items;
    }

    private void applyScopeAdjustments(
            List<FinancialDocumentItem> items,
            Long facilityId,
            Currency currency,
            LocalDate pricingDate,
            TaxApplicableOn taxApplicableOn,
            DiscountApplicableOn discountApplicableOn
    ) {
        BillingAdjustmentResolveResponse rules =
                resolveRules(
                        facilityId,
                        currency,
                        pricingDate,
                        taxApplicableOn,
                        discountApplicableOn
                );

        if (!hasDiscountRule(rules) && !hasTaxRule(rules)) {
            return;
        }

        if (taxApplicableOn == TaxApplicableOn.INVOICE
                || discountApplicableOn == DiscountApplicableOn.INVOICE) {
            applyInvoiceLevelAdjustments(items, rules);
            return;
        }

        for (FinancialDocumentItem item : items) {
            applySingleLineAdjustment(item, rules);
        }
    }

    private void applyInvoiceLevelAdjustments(
            List<FinancialDocumentItem> items,
            BillingAdjustmentResolveResponse rules
    ) {
        BigDecimal subtotal =
                items.stream()
                        .map(FinancialDocumentItem::getNetAmount)
                        .map(this::money)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (subtotal.signum() <= 0) {
            return;
        }

        BigDecimal invoiceDiscount =
                calculateDiscountAmount(
                        subtotal,
                        rules
                );
        BigDecimal afterDiscount =
                subtotal
                        .subtract(invoiceDiscount)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal invoiceTax =
                calculateTaxAmount(
                        afterDiscount,
                        rules
                );
        BigDecimal finalTotal =
                afterDiscount
                        .add(invoiceTax)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        distributeProportionally(
                items,
                subtotal,
                invoiceDiscount,
                invoiceTax,
                finalTotal
        );

        invoiceItemPricingSnapshotService.appendInvoiceScopeAdjustments(
                items,
                rules.taxApplicableOn(),
                rules.discountApplicableOn(),
                rules,
                invoiceDiscount,
                invoiceTax
        );

        LOG.info(
                "[INVOICE_ADJUSTMENTS] subtotal={} invoiceDiscount={} invoiceTax={} finalTotal={}",
                subtotal,
                invoiceDiscount,
                invoiceTax,
                finalTotal
        );
    }

    private void distributeProportionally(
            List<FinancialDocumentItem> items,
            BigDecimal subtotal,
            BigDecimal invoiceDiscount,
            BigDecimal invoiceTax,
            BigDecimal finalTotal
    ) {
        BigDecimal allocatedDiscount = BigDecimal.ZERO;
        BigDecimal allocatedTax = BigDecimal.ZERO;
        BigDecimal allocatedNet = BigDecimal.ZERO;

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

            BigDecimal lineFinalNet =
                    isLast
                            ? finalTotal.subtract(allocatedNet)
                            : lineNet
                                    .subtract(lineDiscountShare)
                                    .add(lineTaxShare)
                                    .setScale(
                                            MONEY_SCALE,
                                            RoundingMode.HALF_UP
                                    );

            item.setDiscountAmount(
                    money(item.getDiscountAmount())
                            .add(lineDiscountShare)
            );
            item.setTaxAmount(
                    money(item.getTaxAmount())
                            .add(lineTaxShare)
            );
            item.setNetAmount(lineFinalNet);
            updateShareAmounts(item, lineFinalNet);

            allocatedDiscount =
                    allocatedDiscount.add(lineDiscountShare);
            allocatedTax = allocatedTax.add(lineTaxShare);
            allocatedNet = allocatedNet.add(lineFinalNet);
        }
    }

    private void applySingleLineAdjustment(
            FinancialDocumentItem item,
            BillingAdjustmentResolveResponse rules
    ) {
        BigDecimal baseAmount = money(item.getNetAmount());
        if (baseAmount.signum() <= 0) {
            return;
        }

        BigDecimal discountAmount =
                calculateDiscountAmount(
                        baseAmount,
                        rules
                );
        BigDecimal afterDiscount =
                baseAmount
                        .subtract(discountAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal taxAmount =
                calculateTaxAmount(
                        afterDiscount,
                        rules
                );
        BigDecimal finalNet =
                afterDiscount
                        .add(taxAmount)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        item.setDiscountAmount(
                money(item.getDiscountAmount())
                        .add(discountAmount)
        );
        item.setTaxAmount(
                money(item.getTaxAmount())
                        .add(taxAmount)
        );
        item.setNetAmount(finalNet);
        updateShareAmounts(item, finalNet);

        invoiceItemPricingSnapshotService.appendLineScopeAdjustments(
                item,
                rules,
                discountAmount,
                taxAmount
        );
    }

    private void updateShareAmounts(
            FinancialDocumentItem item,
            BigDecimal finalNet
    ) {
        if (money(item.getPatientShareAmount()).signum() > 0) {
            item.setPatientShareAmount(finalNet);
            item.setRemainingAmount(finalNet);
            return;
        }

        if (money(item.getInsuranceShareAmount()).signum() > 0) {
            item.setInsuranceShareAmount(finalNet);
            item.setInsuranceRemainingAmount(finalNet);
        }
    }

    private BillingAdjustmentResolveResponse resolveRules(
            Long facilityId,
            Currency currency,
            LocalDate pricingDate,
            TaxApplicableOn taxApplicableOn,
            DiscountApplicableOn discountApplicableOn
    ) {
        try {
            return setupBillingAdjustmentClient.resolveAdjustments(
                    new BillingAdjustmentResolveRequest(
                            facilityId,
                            currency,
                            taxApplicableOn,
                            discountApplicableOn,
                            pricingDate
                    )
            );
        } catch (FeignException exception) {
            LOG.warn(
                    "Unable to resolve billing adjustments from setup service "
                            + "facilityId={} taxApplicableOn={} discountApplicableOn={}: {}",
                    facilityId,
                    taxApplicableOn,
                    discountApplicableOn,
                    exception.getMessage()
            );
            return emptyRules();
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Billing adjustment resolution failed facilityId={}: {}",
                    facilityId,
                    exception.getMessage()
            );
            return emptyRules();
        }
    }

    private BigDecimal calculateDiscountAmount(
            BigDecimal baseAmount,
            BillingAdjustmentResolveResponse rules
    ) {
        if (!hasDiscountRule(rules) || baseAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        if (rules.discountType() == DiscountType.PERCENTAGE) {
            return baseAmount
                    .multiply(defaultZero(rules.discountRate()))
                    .divide(
                            BigDecimal.valueOf(100),
                            MONEY_SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        if (rules.discountType() == DiscountType.FIXED_AMOUNT) {
            BigDecimal fixedAmount =
                    defaultZero(rules.discountFixedAmount());
            return fixedAmount.min(baseAmount);
        }

        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal calculateTaxAmount(
            BigDecimal baseAmount,
            BillingAdjustmentResolveResponse rules
    ) {
        if (!hasTaxRule(rules) || baseAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        if (rules.taxType() == TaxType.PERCENTAGE) {
            BigDecimal rate = defaultZero(rules.taxRate());
            if (rules.taxCalculationType()
                    == TaxCalculationType.INCLUSIVE) {
                return baseAmount
                        .multiply(rate)
                        .divide(
                                BigDecimal.valueOf(100).add(rate),
                                MONEY_SCALE,
                                RoundingMode.HALF_UP
                        );
            }

            return baseAmount
                    .multiply(rate)
                    .divide(
                            BigDecimal.valueOf(100),
                            MONEY_SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        if (rules.taxType() == TaxType.FIXED_AMOUNT) {
            return defaultZero(rules.taxFixedAmount());
        }

        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private boolean hasDiscountRule(
            BillingAdjustmentResolveResponse rules
    ) {
        return rules != null
                && rules.discountId() != null
                && rules.discountType() != null;
    }

    private boolean hasTaxRule(
            BillingAdjustmentResolveResponse rules
    ) {
        return rules != null
                && rules.taxId() != null
                && rules.taxType() != null
                && rules.taxType() != TaxType.EXEMPT;
    }

    private BillingAdjustmentResolveResponse emptyRules() {
        return new BillingAdjustmentResolveResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
