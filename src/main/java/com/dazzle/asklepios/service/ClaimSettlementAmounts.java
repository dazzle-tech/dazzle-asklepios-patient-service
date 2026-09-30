package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.ClaimItem;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.FinancialDocumentItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Settlement money for accepted claims. A stored zero stays zero.
 * Another source is used only when the value was never stored.
 */
final class ClaimSettlementAmounts {

    private static final int MONEY_SCALE = 2;

    private ClaimSettlementAmounts() {
    }

    static Amounts forClaim(
            ClaimRequest claim,
            List<ClaimItem> items,
            Map<Long, BillingChargeLine> chargeLines,
            Map<Long, FinancialDocumentItem> documentItemsById,
            Map<Long, List<FinancialDocumentItem>> documentItemsByDocument
    ) {
        BigDecimal billed = presentSum(items, ClaimItem::getNet);
        if (billed == null) {
            billed = money(claim.getTotalNet());
        }

        BigDecimal patientShare = presentSum(items, ClaimItem::getPatientShare);
        if (patientShare == null) {
            patientShare = patientShareFromChargeLines(items, chargeLines);
        }

        BigDecimal insuranceAmount = sum(items, ClaimItem::getPayerShare);
        List<FinancialDocumentItem> documentItems =
                documentItemsFor(claim, items, documentItemsById, documentItemsByDocument);

        BigDecimal paid = sumDocument(documentItems, FinancialDocumentItem::getInsurancePaidAmount);
        BigDecimal outstanding = presentSum(documentItems, FinancialDocumentItem::getInsuranceRemainingAmount);
        if (outstanding == null) {
            outstanding = insuranceAmount.signum() > 0
                    ? money(insuranceAmount.subtract(paid).max(BigDecimal.ZERO))
                    : money(null);
        }

        return new Amounts(
                billed,
                insuranceAmount,
                money(null),
                patientShare,
                insuranceAmount,
                paid,
                outstanding
        );
    }

    static String paymentStatus(Amounts amounts) {
        if (amounts.paidAmount.signum() > 0 && amounts.outstandingAmount.signum() <= 0) {
            return "SETTLED";
        }
        if (amounts.paidAmount.signum() > 0) {
            return "PARTIALLY_SETTLED";
        }
        return "UNSETTLED";
    }

    private static BigDecimal patientShareFromChargeLines(
            List<ClaimItem> items,
            Map<Long, BillingChargeLine> chargeLines
    ) {
        return items.stream()
                .map(ClaimItem::getBillingChargeLineId)
                .filter(Objects::nonNull)
                .map(chargeLines::get)
                .filter(Objects::nonNull)
                .map(BillingChargeLine::getPatientResponsibilityAmount)
                .map(ClaimSettlementAmounts::money)
                .reduce(money(null), BigDecimal::add);
    }

    private static List<FinancialDocumentItem> documentItemsFor(
            ClaimRequest claim,
            List<ClaimItem> items,
            Map<Long, FinancialDocumentItem> documentItemsById,
            Map<Long, List<FinancialDocumentItem>> documentItemsByDocument
    ) {
        List<FinancialDocumentItem> linked = items.stream()
                .map(ClaimItem::getFinancialDocumentItemId)
                .filter(Objects::nonNull)
                .map(documentItemsById::get)
                .filter(Objects::nonNull)
                .toList();
        if (!linked.isEmpty()) {
            return linked;
        }
        return documentItemsByDocument.getOrDefault(claim.getFinancialDocumentId(), List.of());
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> getter) {
        return rows.stream()
                .map(getter)
                .map(ClaimSettlementAmounts::money)
                .reduce(money(null), BigDecimal::add);
    }

    private static <T> BigDecimal sumDocument(List<T> rows, Function<T, BigDecimal> getter) {
        return sum(rows, getter);
    }

    /**
     * Null when every stored value is missing. Zero when a zero was stored.
     */
    private static <T> BigDecimal presentSum(List<T> rows, Function<T, BigDecimal> getter) {
        boolean anyPresent = false;
        BigDecimal total = money(null);
        for (T row : rows) {
            BigDecimal value = getter.apply(row);
            if (value == null) {
                continue;
            }
            anyPresent = true;
            total = total.add(money(value));
        }
        return anyPresent ? total : null;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    record Amounts(
            BigDecimal billedAmount,
            BigDecimal approvedAmount,
            BigDecimal rejectedAmount,
            BigDecimal patientShare,
            BigDecimal insuranceAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount
    ) {
    }
}
