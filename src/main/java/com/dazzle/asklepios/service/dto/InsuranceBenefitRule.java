package com.dazzle.asklepios.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Structured benefit rule extracted from Waseel eligibility or stored on patient insurance.
 *
 * Financial semantics:
 * - {@link #patientCopaymentPercentage()} / {@link #patientMaximumCopayment()} — patient copay per service
 * - {@link #maximumBenefit()} — insurance coverage cap (e.g. Maximum benefit allowable)
 * - {@link #approvalLimit()} — pre-authorization limit on insurance share
 */
public record InsuranceBenefitRule(

        Long id,

        String benefitCategory,

        String itemName,

        String itemCode,

        String networkType,

        String providerType,

        String term,

        String unit,

        String currency,

        BigDecimal maximumBenefit,

        BigDecimal approvalLimit,

        BigDecimal patientCopaymentPercentage,

        BigDecimal patientMaximumCopayment,

        boolean globalDefault,

        String exceptionsJson

) implements Serializable {

    public static final String GLOBAL_CATEGORY = "__GLOBAL__";

    public InsuranceBenefitRule withoutId() {
        return new InsuranceBenefitRule(
                null,
                benefitCategory,
                itemName,
                itemCode,
                networkType,
                providerType,
                term,
                unit,
                currency,
                maximumBenefit,
                approvalLimit,
                patientCopaymentPercentage,
                patientMaximumCopayment,
                globalDefault,
                exceptionsJson
        );
    }
}
