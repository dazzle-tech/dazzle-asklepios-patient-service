package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.TaxCalculationType;
import com.dazzle.asklepios.domain.enumeration.billing.CalculationOrder;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.RoundingModeType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PartyTaxDistribution;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional
public class BillingPricingService {

    private static final Logger LOG =
            LoggerFactory.getLogger(BillingPricingService.class);

    private static final String ENTITY_NAME =
            "billingPricing";

    private static final int MONEY_SCALE = 4;

    public void calculate(
            BillingProcessingContext context
    ) {
        LOG.debug(
                "[CALCULATE] Billing pricing pspId={}",
                getPspId(context)
        );

        PatientServiceAndProduct item =
                requirePatientServiceProduct(context);

        BillingPricingInput input =
                requirePricingInput(context);

        validatePricingInput(item, input);

        int scale = input.roundingScale() == null
                ? 4
                : input.roundingScale();

        RoundingMode roundingMode =
                resolveRoundingMode(
                        input.roundingMode()
                );

        BigDecimal quantity =
                input.quantity()
                        .setScale(scale, roundingMode);

        BigDecimal unitPrice =
                input.unitPrice()
                        .setScale(scale, roundingMode);

        BigDecimal grossAmount =
                quantity
                        .multiply(unitPrice)
                        .setScale(scale, roundingMode);

        BigDecimal discountAmount =
                calculateDiscount(
                        grossAmount,
                        input.discountType(),
                        input.discountRate(),
                        input.configuredDiscountAmount(),
                        scale,
                        roundingMode
                );

        BigDecimal amountAfterDiscount =
                grossAmount
                        .subtract(discountAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(scale, roundingMode);

        BigDecimal exemptionAmount =
                calculateExemption(
                        item,
                        amountAfterDiscount,
                        scale,
                        roundingMode
                );

        BigDecimal amountAfterExemption =
                amountAfterDiscount
                        .subtract(exemptionAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(scale, roundingMode);

        BigDecimal taxableAmount;
        BigDecimal taxAmount;
        BigDecimal netAmount;
        boolean taxIncludedInPrice =
                input.taxCalculationType()
                        == TaxCalculationType.INCLUSIVE;

        if (input.calculationOrder()
                == CalculationOrder.TAX_THEN_DISCOUNT) {

            BigDecimal taxBeforeDiscount =
                    calculateTax(
                            grossAmount,
                            input.taxType(),
                            input.taxRate(),
                            input.taxCalculationType(),
                            scale,
                            roundingMode
                    );

            taxableAmount = amountAfterExemption;

            taxAmount = Boolean.TRUE.equals(
                    item.getIsExempted()
            )
                    ? BigDecimal.ZERO.setScale(
                    scale,
                    roundingMode
            )
                    : taxBeforeDiscount;

            netAmount = grossAmount
                    .subtract(discountAmount)
                    .subtract(exemptionAmount);

            if (!taxIncludedInPrice) {
                netAmount = netAmount.add(taxAmount);
            }

            netAmount = netAmount
                    .max(BigDecimal.ZERO)
                    .setScale(scale, roundingMode);

        } else {

            taxableAmount =
                    amountAfterExemption;

            taxAmount =
                    calculateTax(
                            taxableAmount,
                            input.taxType(),
                            input.taxRate(),
                            input.taxCalculationType(),
                            scale,
                            roundingMode
                    );

            netAmount = taxIncludedInPrice
                    ? taxableAmount
                    : taxableAmount.add(taxAmount);

            netAmount = netAmount
                    .max(BigDecimal.ZERO)
                    .setScale(scale, roundingMode);
        }

        PriceCalculationResult result =
                new PriceCalculationResult(
                        quantity,
                        unitPrice,
                        grossAmount,
                        discountAmount,
                        exemptionAmount,
                        taxableAmount,
                        taxAmount,
                        netAmount
                );

        context.setPricingResult(result);

        LOG.info(
                "[CALCULATE] Billing pricing success "
                        + "pspId={} gross={} discount={} "
                        + "exemption={} tax={} net={}",
                item.getId(),
                grossAmount,
                discountAmount,
                exemptionAmount,
                taxAmount,
                netAmount
        );
    }

    /**
     * Splits the tax calculated on one item between the patient share and the
     * insurance share, then removes the portion of a party whose toggle is off.
     *
     * The shares are the responsibility amounts produced by the existing
     * contract / coverage rules on the tax-inclusive net amount. Each party's
     * tax portion is proportional to its own share, so:
     *
     * - Patient Copayment controls only the patient's tax portion.
     * - Claim Share controls only the insurance's tax portion.
     * - A party with a zero share carries zero tax.
     * - A removed portion is never moved to the other party.
     *
     * For self-pay the caller passes a zero insurance share, so the whole tax
     * belongs to the patient and only Patient Copayment applies.
     */
    public PartyTaxDistribution distributeTax(
            BigDecimal calculatedTax,
            BigDecimal patientShare,
            BigDecimal insuranceShare,
            Boolean taxOnPatientCopayment,
            Boolean taxOnClaimShare
    ) {
        BigDecimal tax = money(calculatedTax);
        BigDecimal patient = money(patientShare);
        BigDecimal insurance = money(insuranceShare);

        BigDecimal patientTaxPortion;

        if (tax.signum() == 0 || patient.signum() == 0) {
            patientTaxPortion = money(BigDecimal.ZERO);
        } else if (insurance.signum() == 0) {
            patientTaxPortion = tax;
        } else {
            patientTaxPortion =
                    tax.multiply(patient)
                            .divide(
                                    patient.add(insurance),
                                    MONEY_SCALE,
                                    RoundingMode.HALF_UP
                            );
        }

        BigDecimal insuranceTaxPortion =
                insurance.signum() == 0
                        ? money(BigDecimal.ZERO)
                        : tax.subtract(patientTaxPortion);

        BigDecimal patientTax =
                isApplied(taxOnPatientCopayment)
                        ? patientTaxPortion
                        : money(BigDecimal.ZERO);

        BigDecimal insuranceTax =
                isApplied(taxOnClaimShare)
                        ? insuranceTaxPortion
                        : money(BigDecimal.ZERO);

        return new PartyTaxDistribution(
                tax,
                patientTax,
                insuranceTax,
                patient.subtract(patientTaxPortion).add(patientTax),
                insurance.subtract(insuranceTaxPortion).add(insuranceTax)
        );
    }

    /**
     * Taxes saved before the toggles existed carry no value; they applied tax
     * to every share, so a missing toggle means "applied".
     */
    private boolean isApplied(Boolean toggle) {
        return !Boolean.FALSE.equals(toggle);
    }

    private BigDecimal money(BigDecimal value) {
        return defaultZero(value)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDiscount(
            BigDecimal grossAmount,
            DiscountType discountType,
            BigDecimal discountRate,
            BigDecimal configuredDiscountAmount,
            int scale,
            RoundingMode roundingMode
    ) {
        if (discountType == null) {
            return BigDecimal.ZERO
                    .setScale(scale, roundingMode);
        }

        BigDecimal result;

        switch (discountType) {

            case PERCENTAGE -> {
                BigDecimal rate =
                        defaultZero(discountRate);

                validateRate(
                        rate,
                        "discountRate"
                );

                result =
                        grossAmount
                                .multiply(rate)
                                .divide(
                                        BigDecimal.valueOf(100),
                                        scale,
                                        roundingMode
                                );
            }

            case FIXED_AMOUNT -> {
                result =
                        defaultZero(
                                configuredDiscountAmount
                        );
            }

            default -> throw new BadRequestAlertException(
                    "Unsupported discount type: "
                            + discountType,
                    ENTITY_NAME,
                    "discountType.unsupported"
            );
        }

        if (result.compareTo(grossAmount) > 0) {
            throw new BadRequestAlertException(
                    "Discount amount cannot exceed gross amount.",
                    ENTITY_NAME,
                    "discount.exceedsGross"
            );
        }

        return result.setScale(scale, roundingMode);
    }

    private BigDecimal calculateExemption(
            PatientServiceAndProduct item,
            BigDecimal amountAfterDiscount,
            int scale,
            RoundingMode roundingMode
    ) {
        if (!Boolean.TRUE.equals(
                item.getIsExempted()
        )) {
            return defaultZero(
                    item.getExemptionAmount()
            ).setScale(scale, roundingMode);
        }

        /*
         * Full exemption:
         * The complete amount after discount is exempted.
         */
        return amountAfterDiscount
                .setScale(scale, roundingMode);
    }

    private BigDecimal calculateTax(
            BigDecimal taxableAmount,
            TaxType taxType,
            BigDecimal taxRate,
            TaxCalculationType calculationType,
            int scale,
            RoundingMode roundingMode
    ) {
        if (taxType == null
                || taxableAmount.signum() == 0) {
            return BigDecimal.ZERO
                    .setScale(scale, roundingMode);
        }

        BigDecimal rate =
                defaultZero(taxRate);

        validateRate(rate, "taxRate");

        return switch (taxType) {

            case PERCENTAGE -> {
                BigDecimal divisor =
                        calculationType == TaxCalculationType.INCLUSIVE
                                ? BigDecimal.valueOf(100).add(rate)
                                : BigDecimal.valueOf(100);

                yield taxableAmount
                        .multiply(rate)
                        .divide(
                                divisor,
                                scale,
                                roundingMode
                        )
                        .setScale(scale, roundingMode);
            }

            case FIXED_AMOUNT -> {
                BigDecimal fixedAmount =
                        rate.setScale(scale, roundingMode);

                if (calculationType == TaxCalculationType.INCLUSIVE
                        && fixedAmount.compareTo(taxableAmount) > 0) {
                    yield taxableAmount.setScale(scale, roundingMode);
                }

                yield fixedAmount;
            }
            default -> throw new IllegalStateException("Unexpected value: " + taxType);
        };
    }

    private void validatePricingInput(
            PatientServiceAndProduct item,
            BillingPricingInput input
    ) {
        if (input.quantity() == null
                || input.quantity().signum() <= 0) {
            throw new BadRequestAlertException(
                    "Billing quantity must be greater than zero.",
                    ENTITY_NAME,
                    "quantity.invalid"
            );
        }

        if (input.unitPrice() == null
                || input.unitPrice().signum() < 0) {
            throw new BadRequestAlertException(
                    "Unit price cannot be negative.",
                    ENTITY_NAME,
                    "unitPrice.invalid"
            );
        }

        if (input.currency() == null) {
            throw new BadRequestAlertException(
                    "Pricing currency is required.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        if (item.getCurrency() != input.currency()) {
            throw new BadRequestAlertException(
                    "Item currency does not match "
                            + "resolved pricing currency.",
                    ENTITY_NAME,
                    "currency.mismatch"
            );
        }
    }

    private void validateRate(
            BigDecimal rate,
            String field
    ) {
        if (rate.signum() < 0
                || rate.compareTo(
                BigDecimal.valueOf(100)
        ) > 0) {
            throw new BadRequestAlertException(
                    field + " must be between 0 and 100.",
                    ENTITY_NAME,
                    field + ".invalid"
            );
        }
    }

    private RoundingMode resolveRoundingMode(
            RoundingModeType roundingModeType
    ) {
        if (roundingModeType == null) {
            return RoundingMode.HALF_UP;
        }

        return switch (roundingModeType) {
            case HALF_UP -> RoundingMode.HALF_UP;
            case HALF_DOWN -> RoundingMode.HALF_DOWN;
            case HALF_EVEN -> RoundingMode.HALF_EVEN;
            case UP -> RoundingMode.UP;
            case DOWN -> RoundingMode.DOWN;
            case CEILING -> RoundingMode.CEILING;
            case FLOOR -> RoundingMode.FLOOR;

            default -> throw new IllegalStateException("Unexpected value: " + roundingModeType);
        };
    }

    private PatientServiceAndProduct
    requirePatientServiceProduct(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getPatientServiceProduct()
                == null) {
            throw new BadRequestAlertException(
                    "Patient service/product is required.",
                    ENTITY_NAME,
                    "patientServiceProduct.required"
            );
        }

        return context.getPatientServiceProduct();
    }

    private BillingPricingInput requirePricingInput(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getPricingInput() == null) {
            throw new BadRequestAlertException(
                    "Resolved pricing input is required.",
                    ENTITY_NAME,
                    "pricingInput.required"
            );
        }

        return context.getPricingInput();
    }

    private Long getPspId(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getPatientServiceProduct()
                == null) {
            return null;
        }

        return context
                .getPatientServiceProduct()
                .getId();
    }

    private BigDecimal defaultZero(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }
}