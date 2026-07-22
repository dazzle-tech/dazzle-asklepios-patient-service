package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.CalculationOrder;
import com.dazzle.asklepios.domain.enumeration.billing.RoundingModeType;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BillingPricingInputFactory {

    private static final String ENTITY_NAME =
            "billingPricingInput";

    public BillingPricingInput create(
            PatientServiceAndProduct item,
            BillingPricingResolveResponse response
    ) {
        if (item == null) {
            throw new BadRequestAlertException(
                    "Patient service/product is required.",
                    ENTITY_NAME,
                    "item.required"
            );
        }

        if (response == null) {
            throw new BadRequestAlertException(
                    "Resolved pricing response is required.",
                    ENTITY_NAME,
                    "response.required"
            );
        }

        return new BillingPricingInput(
                response.priceListId(),
                response.priceListItemId(),
                response.priceListCode(),
                response.priceListName(),
                response.priceListItemCode(),
                response.pricingVersion(),

                BigDecimal.valueOf(item.getQuantity()),
                response.unitPrice(),

                response.discountId(),
                response.discountType(),
                defaultZero(response.discountRate()),
                defaultZero(response.discountFixedAmount()),

                response.taxId(),
                response.taxType(),
                response.taxCalculationType(),
                defaultZero(response.taxRate()),
                defaultZero(response.taxFixedAmount()),

                response.currency(),
                response.pricingSource(),

                resolveCalculationOrder(
                        response.calculationOrder()
                ),
                resolveRoundingMode(
                        response.roundingMode()
                ),
                response.roundingScale() == null
                        ? 4
                        : response.roundingScale(),

                response.itemCode(),
                response.itemName()
        );
    }

    private CalculationOrder resolveCalculationOrder(
            String value
    ) {
        if ("TAX_THEN_DISCOUNT".equals(value)) {
            return CalculationOrder.TAX_THEN_DISCOUNT;
        }

        return CalculationOrder.DISCOUNT_THEN_TAX;
    }

    private RoundingModeType resolveRoundingMode(
            String value
    ) {
        if ("HALF_DOWN".equals(value)) {
            return RoundingModeType.HALF_DOWN;
        }

        if ("HALF_EVEN".equals(value)) {
            return RoundingModeType.HALF_EVEN;
        }

        if ("UP".equals(value)) {
            return RoundingModeType.UP;
        }

        if ("DOWN".equals(value)) {
            return RoundingModeType.DOWN;
        }

        if ("CEILING".equals(value)) {
            return RoundingModeType.CEILING;
        }

        if ("FLOOR".equals(value)) {
            return RoundingModeType.FLOOR;
        }

        return RoundingModeType.HALF_UP;
    }

    private BigDecimal defaultZero(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }
}