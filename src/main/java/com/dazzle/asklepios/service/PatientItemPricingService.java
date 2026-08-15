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
 * Applies catalog pricing to a billing item before charge-line creation
 * or pre-authorization submission.
 *
 * Resolution order is always:
 * 1. Applicable price list for the patient's coverage / payer
 * 2. Setup item base price fallback
 */
@Service
public class PatientItemPricingService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientItemPricingService.class);

    private static final String ENTITY_NAME = "patientItemPricing";

    private final BillingEngineService billingEngineService;
    private final BillingPricingInputFactory billingPricingInputFactory;
    private final BillingPricingService billingPricingService;

    public PatientItemPricingService(
            @Lazy BillingEngineService billingEngineService,
            BillingPricingInputFactory billingPricingInputFactory,
            BillingPricingService billingPricingService
    ) {
        this.billingEngineService = billingEngineService;
        this.billingPricingInputFactory = billingPricingInputFactory;
        this.billingPricingService = billingPricingService;
    }

    public void applyResolvedPricing(PatientServiceAndProduct item, Long facilityId) {
        if (item == null) {
            throw new BadRequestAlertException(
                    "Billing item is required to resolve pricing.",
                    ENTITY_NAME,
                    "item.required"
            );
        }

        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to resolve item pricing.",
                    ENTITY_NAME,
                    "encounter.facility.required"
            );
        }

        if (item.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Currency is required to resolve item pricing.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(item, facilityId);

        item.setCurrency(resolvedPrice.currency());

        BillingPricingInput pricingInput =
                billingPricingInputFactory.create(item, resolvedPrice);

        BillingProcessingContext context = BillingProcessingContext.builder()
                .patientServiceProduct(item)
                .pricingInput(pricingInput)
                .build();

        billingPricingService.calculate(context);

        PriceCalculationResult pricing = context.getPricingResult();
        if (pricing == null) {
            throw new BadRequestAlertException(
                    "Resolved pricing calculation produced no result.",
                    ENTITY_NAME,
                    "pricingResult.missing"
            );
        }

        item.setUnitPrice(pricing.unitPrice());
        item.setGrossAmount(pricing.grossAmount());
        item.setDiscountAmount(defaultZero(pricing.discountAmount()));
        item.setExemptionAmount(defaultZero(pricing.exemptionAmount()));
        item.setTaxAmount(defaultZero(pricing.taxAmount()));
        item.setNetAmount(pricing.netAmount());
        item.setTotalAmount(pricing.netAmount());
        item.setRemainingAmount(pricing.netAmount());
        item.setPriceSource(mapPriceSource(resolvedPrice.priceSource()));

        LOG.info(
                "[ITEM_PRICING] Resolved unitPrice={} net={} discount={} currency={} priceSource={} "
                        + "billingItemType={} procedureId={} serviceId={} diagnosticTestId={} brandMedicationId={}",
                pricing.unitPrice(),
                pricing.netAmount(),
                pricing.discountAmount(),
                resolvedPrice.currency(),
                resolvedPrice.priceSource(),
                item.getBillingItemType(),
                item.getProcedureId(),
                item.getServiceId(),
                item.getDiagnosticTestId(),
                item.getBrandMedicationId()
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
