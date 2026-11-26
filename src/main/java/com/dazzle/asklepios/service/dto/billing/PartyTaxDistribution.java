package com.dazzle.asklepios.service.dto.billing;

import java.math.BigDecimal;

/**
 * Tax of one billing item split between the patient share and the insurance
 * share, after the Patient Copayment / Claim Share toggles were applied.
 *
 * patientAmount / insuranceAmount are the final responsibility amounts
 * (share before tax + the tax that party carries).
 */
public record PartyTaxDistribution(

        BigDecimal calculatedTaxAmount,

        BigDecimal patientTaxAmount,

        BigDecimal insuranceTaxAmount,

        BigDecimal patientAmount,

        BigDecimal insuranceAmount

) {

    public BigDecimal appliedTaxAmount() {
        return patientTaxAmount.add(insuranceTaxAmount);
    }

    public BigDecimal netAmount() {
        return patientAmount.add(insuranceAmount);
    }
}
