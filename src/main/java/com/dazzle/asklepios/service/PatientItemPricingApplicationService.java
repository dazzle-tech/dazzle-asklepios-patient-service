package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Applies the same price-list-then-setup resolution used by billing onto a
 * patient item before pre-authorization, without creating charge lines.
 */
@Service
public class PatientItemPricingApplicationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientItemPricingApplicationService.class);

    private final BillingEngineService billingEngineService;
    private final BillingPricingInputFactory billingPricingInputFactory;
    private final BillingPricingService billingPricingService;

    public PatientItemPricingApplicationService(
            @Lazy BillingEngineService billingEngineService,
            BillingPricingInputFactory billingPricingInputFactory,
            BillingPricingService billingPricingService
    ) {
        this.billingEngineService = billingEngineService;
        this.billingPricingInputFactory = billingPricingInputFactory;
        this.billingPricingService = billingPricingService;
    }

    public void applyToItem(PatientServiceAndProduct item, Long facilityId) {
        if (item == null) {
            return;
        }

        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to resolve item pricing.",
                    "patientItemPricing",
                    "encounter.facility.required"
            );
        }

        if (item.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Currency is required to resolve item pricing.",
                    "patientItemPricing",
                    "currency.required"
            );
        }

        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(item, facilityId);

        BillingPricingInput pricingInput =
                billingPricingInputFactory.create(item, resolvedPrice);

        BillingProcessingContext context = BillingProcessingContext.builder()
                .patientServiceProduct(item)
                .pricingInput(pricingInput)
                .build();

        billingPricingService.calculate(context);

        PriceCalculationResult result = context.getPricingResult();
        BigDecimal unitPrice = result.unitPrice();
        BigDecimal discountAmount = defaultZero(result.discountAmount());
        BigDecimal taxAmount = defaultZero(result.taxAmount());
        BigDecimal netAmount = result.netAmount();

        item.setUnitPrice(unitPrice);
        item.setDiscountAmount(discountAmount);
        item.setTaxAmount(taxAmount);
        item.setGrossAmount(result.grossAmount());
        item.setNetAmount(netAmount);
        item.setTotalAmount(netAmount);
        item.setRemainingAmount(netAmount);
        item.setCurrency(resolvedPrice.currency());
        item.setPriceSource(mapPriceSource(resolvedPrice.priceSource()));

        LOG.info(
                "[ITEM_PRICING] Applied price-list/setup pricing pspId={} itemType={} "
                        + "source={} unitPrice={} discount={} net={}",
                item.getId(),
                item.getBillingItemType(),
                resolvedPrice.priceSource(),
                unitPrice,
                discountAmount,
                netAmount
        );
    }

    private PriceSource mapPriceSource(BillingPriceSource source) {
        if (source == BillingPriceSource.PRICE_LIST) {
            return PriceSource.PRICE_LIST;
        }

        return PriceSource.DEFAULT;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
