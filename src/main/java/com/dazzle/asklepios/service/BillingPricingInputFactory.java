package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.CalculationOrder;
import com.dazzle.asklepios.domain.enumeration.billing.PricingSource;
import com.dazzle.asklepios.domain.enumeration.billing.RoundingModeType;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BillingPricingInputFactory {

    private static final String ENTITY_NAME =
            "billingPricingInput";

    public BillingPricingInput create(
            PatientServiceAndProduct item,
            ResolvedBillingPrice resolvedPrice
    ) {
        validateInput(
                item,
                resolvedPrice
        );

        if (resolvedPrice.resolvedFromPriceList()) {
            return createFromPriceList(
                    item,
                    resolvedPrice
            );
        }

        return createFromSetupFallback(
                item,
                resolvedPrice
        );
    }

    private BillingPricingInput createFromPriceList(
            PatientServiceAndProduct item,
            ResolvedBillingPrice resolvedPrice
    ) {
        BillingPricingResolveResponse response =
                resolvedPrice.pricingResponse();

        if (response == null) {
            throw new BadRequestAlertException(
                    "Price-list pricing response is required.",
                    ENTITY_NAME,
                    "priceListResponse.required"
            );
        }

        return new BillingPricingInput(
                resolvedPrice.priceListId(),
                resolvedPrice.priceListItemId(),
                response.priceListCode(),
                response.priceListName(),
                response.priceListItemCode(),
                response.pricingVersion(),

                BillingPriceSource.PRICE_LIST,
                resolvedPrice.setupSourceId(),

                BigDecimal.valueOf(
                        item.getQuantity()
                ),
                resolvedPrice.unitPrice(),
                resolvedPrice.setupUnitPrice(),

                response.discountId(),
                response.discountType(),
                defaultZero(
                        response.discountRate()
                ),
                defaultZero(
                        response.discountFixedAmount()
                ),

                response.taxId(),
                response.taxType(),
                response.taxCalculationType(),
                defaultZero(
                        response.taxRate()
                ),
                defaultZero(
                        response.taxFixedAmount()
                ),

                resolvedPrice.currency(),
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

    private BillingPricingInput createFromSetupFallback(
            PatientServiceAndProduct item,
            ResolvedBillingPrice resolvedPrice
    ) {
        /*
         * Setup fallback provides only the base item price.
         *
         * No price-list discount or price-list tax metadata exists.
         * Therefore:
         *
         * - discount is zero
         * - tax is zero
         * - default calculation and rounding settings are used
         *
         * Later, if tax and discount are resolved independently from
         * Setup configuration, they can be injected here.
         */
        return new BillingPricingInput(
                null,
                null,
                null,
                null,
                null,
                null,

                BillingPriceSource.SETUP_FALLBACK,
                resolvedPrice.setupSourceId(),

                BigDecimal.valueOf(
                        item.getQuantity()
                ),
                resolvedPrice.unitPrice(),
                resolvedPrice.setupUnitPrice(),

                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,

                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,

                resolvedPrice.currency(),
                resolveFallbackPricingSource(),

                CalculationOrder.DISCOUNT_THEN_TAX,
                RoundingModeType.HALF_UP,
                4,

                resolveFallbackItemCode(item),
                resolveFallbackItemName(item)
        );
    }

    private void validateInput(
            PatientServiceAndProduct item,
            ResolvedBillingPrice resolvedPrice
    ) {
        if (item == null) {
            throw new BadRequestAlertException(
                    "Patient service/product is required.",
                    ENTITY_NAME,
                    "item.required"
            );
        }

        if (resolvedPrice == null) {
            throw new BadRequestAlertException(
                    "Resolved billing price is required.",
                    ENTITY_NAME,
                    "resolvedPrice.required"
            );
        }

        if (resolvedPrice.priceSource() == null) {
            throw new BadRequestAlertException(
                    "Billing price source is required.",
                    ENTITY_NAME,
                    "priceSource.required"
            );
        }

        if (resolvedPrice.unitPrice() == null
                || resolvedPrice.unitPrice().signum() < 0) {
            throw new BadRequestAlertException(
                    "Resolved unit price is invalid.",
                    ENTITY_NAME,
                    "unitPrice.invalid"
            );
        }

        if (resolvedPrice.currency() == null) {
            throw new BadRequestAlertException(
                    "Resolved currency is required.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        if (item.getQuantity() == null
                || item.getQuantity() <= 0) {
            throw new BadRequestAlertException(
                    "Item quantity must be greater than zero.",
                    ENTITY_NAME,
                    "quantity.invalid"
            );
        }

        if (resolvedPrice.currency()
                != item.getCurrency()) {
            throw new BadRequestAlertException(
                    "Resolved price currency does not match item currency.",
                    ENTITY_NAME,
                    "currency.mismatch"
            );
        }

        if (resolvedPrice.resolvedFromPriceList()
                && !resolvedPrice.hasPriceListMetadata()) {
            throw new BadRequestAlertException(
                    "Price-list metadata is incomplete.",
                    ENTITY_NAME,
                    "priceListMetadata.incomplete"
            );
        }

        if (resolvedPrice.resolvedFromSetupFallback()
                && resolvedPrice.setupSourceId() == null) {
            throw new BadRequestAlertException(
                    "Setup source ID is required for fallback pricing.",
                    ENTITY_NAME,
                    "setupSourceId.required"
            );
        }
    }

    private PricingSource resolveFallbackPricingSource() {
        /*
         * Use the closest existing enum value that represents
         * the standard/default item price.
         *
         * If your PricingSource enum contains a value such as:
         *
         * SETUP
         * BASE_PRICE
         * STANDARD
         *
         * replace DEFAULT below with that exact value.
         */
        return PricingSource.DEFAULT_ITEM_PRICE;
    }

    private String resolveFallbackItemCode(
            PatientServiceAndProduct item
    ) {
        if (item.getSourceId() != null) {
            return item.getBillingItemType().name()
                    + "-"
                    + item.getSourceId();
        }

        return item.getBillingItemType().name()
                + "-"
                + item.getId();
    }

    private String resolveFallbackItemName(
            PatientServiceAndProduct item
    ) {
        return item.getBillingItemType().name()
                .replace(
                        '_',
                        ' '
                );
    }

    private CalculationOrder resolveCalculationOrder(
            String value
    ) {
        if ("TAX_THEN_DISCOUNT".equalsIgnoreCase(
                value
        )) {
            return CalculationOrder.TAX_THEN_DISCOUNT;
        }

        return CalculationOrder.DISCOUNT_THEN_TAX;
    }

    private RoundingModeType resolveRoundingMode(
            String value
    ) {
        if ("HALF_DOWN".equalsIgnoreCase(value)) {
            return RoundingModeType.HALF_DOWN;
        }

        if ("HALF_EVEN".equalsIgnoreCase(value)) {
            return RoundingModeType.HALF_EVEN;
        }

        if ("UP".equalsIgnoreCase(value)) {
            return RoundingModeType.UP;
        }

        if ("DOWN".equalsIgnoreCase(value)) {
            return RoundingModeType.DOWN;
        }

        if ("CEILING".equalsIgnoreCase(value)) {
            return RoundingModeType.CEILING;
        }

        if ("FLOOR".equalsIgnoreCase(value)) {
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