package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Unified pricing resolution result.
 *
 * This DTO represents the final base price regardless of whether
 * it was resolved from:
 *
 * - an applicable Price List
 * - the Setup item base price fallback
 */
public record ResolvedBillingPrice(

        BigDecimal unitPrice,

        Currency currency,

        BillingPriceSource priceSource,

        Long setupSourceId,

        Long priceListId,

        Long priceListItemId,

        BillingPricingResolveResponse pricingResponse

) implements Serializable {

    public boolean resolvedFromPriceList() {
        return priceSource
                == BillingPriceSource.PRICE_LIST;
    }

    public boolean resolvedFromSetupFallback() {
        return priceSource
                == BillingPriceSource.SETUP_FALLBACK;
    }

    public boolean hasPriceListMetadata() {
        return priceListId != null
                && priceListItemId != null;
    }
}