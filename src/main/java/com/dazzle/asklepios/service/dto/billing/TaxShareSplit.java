package com.dazzle.asklepios.service.dto.billing;

import java.math.BigDecimal;

/**
 * Patient / insurance split of an insurance item after the tax setup flags.
 *
 * taxAmount / netAmount are the item tax and net actually charged.
 * patientTaxAmount / insuranceTaxAmount are null when the original
 * calculation applies (tax shared proportionally).
 */
public record TaxShareSplit(
        BigDecimal patientShare,
        BigDecimal insuranceShare,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        BigDecimal patientTaxAmount,
        BigDecimal insuranceTaxAmount
) {

    public boolean splitsByShare() {
        return patientTaxAmount != null
                && insuranceTaxAmount != null;
    }
}
