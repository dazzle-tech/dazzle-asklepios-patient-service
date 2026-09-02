package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.service.VisitMaxLimitTracker;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
public class BillingProcessingContext {

    private UUID transactionGroupId;

    private String idempotencyKey;

    private BillingEventType eventType;

    private Long billingRuleId;

    private PatientServiceAndProduct patientServiceProduct;

    private BillingCharge charge;

    private BillingChargeLine chargeLine;


    private BillingPricingSnapshot pricingSnapshot;

    private BillingChargeResponsibility patientResponsibility;

    private BillingChargeResponsibility insuranceResponsibility;

    private BillingReservation reservation;

    private BillingPricingInput pricingInput;

    private PriceCalculationResult pricingResult;

    /**
     * Optional running visit max-limit pool. When set, insurance splits consume
     * the remaining visit cap instead of applying maxLimit per service.
     */
    private VisitMaxLimitTracker visitMaxLimitTracker;

    @Builder.Default
    private BigDecimal patientResponsibilityAmount =
            BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal insuranceResponsibilityAmount =
            BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal otherPayerResponsibilityAmount =
            BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal reservedAmount =
            BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal uncoveredPatientAmount =
            BigDecimal.ZERO;

    public Long getChargeId() {
        return charge == null ? null : charge.getId();
    }

    public Long getChargeLineId() {
        return chargeLine == null ? null : chargeLine.getId();
    }

    public Long getPricingSnapshotId() {
        return pricingSnapshot == null
                ? null
                : pricingSnapshot.getId();
    }

    public BigDecimal getNetAmount() {
        if (pricingResult != null
                && pricingResult.netAmount() != null) {
            return pricingResult.netAmount();
        }

        if (chargeLine != null
                && chargeLine.getNetAmount() != null) {
            return chargeLine.getNetAmount();
        }

        return BigDecimal.ZERO;
    }
}