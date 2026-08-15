package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.PriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Applies price-list/setup pricing with discount/tax and insurance plan split
 * onto {@link PatientServiceAndProduct} rows before charge-line billing exists.
 */
@Service
public class PatientItemPricingApplicationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientItemPricingApplicationService.class);

    private static final String ENTITY_NAME = "patientItemPricing";

    private final BillingEngineService billingEngineService;
    private final BillingPricingInputFactory billingPricingInputFactory;
    private final BillingPricingService billingPricingService;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final BillingChargeService billingChargeService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    public PatientItemPricingApplicationService(
            @Lazy BillingEngineService billingEngineService,
            BillingPricingInputFactory billingPricingInputFactory,
            BillingPricingService billingPricingService,
            InsurancePatientShareCalculator insurancePatientShareCalculator,
            PatientInsuranceRepository patientInsuranceRepository,
            PatientEncounterRepository patientEncounterRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            @Lazy BillingChargeService billingChargeService,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService
    ) {
        this.billingEngineService = billingEngineService;
        this.billingPricingInputFactory = billingPricingInputFactory;
        this.billingPricingService = billingPricingService;
        this.insurancePatientShareCalculator = insurancePatientShareCalculator;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.billingChargeService = billingChargeService;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
    }

    /**
     * Re-applies price-list pricing and insurance plan split for every active item on the encounter.
     * Items with an open charge line sync from billed amounts; others are repriced in-place.
     */
    @Transactional
    public int reapplyInsurancePlanForEncounter(Long encounterId, Long facilityId) {
        if (encounterId == null || facilityId == null) {
            return 0;
        }

        if (!encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(encounterId)) {
            return 0;
        }

        Long encounterInsuranceId =
                encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(
                        encounterId
                );

        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository.findByEncounterId(encounterId);

        int updated = 0;
        for (PatientServiceAndProduct item : items) {
            if (item == null
                    || item.getPaymentStatus() == PaymentStatus.CANCELLED
                    || Boolean.TRUE.equals(item.getIsExempted())) {
                continue;
            }

            if (item.getPatientInsuranceId() == null && encounterInsuranceId != null) {
                item.setPatientInsuranceId(encounterInsuranceId);
            }

            if (billingChargeService
                    .findActiveChargeLine(item.getId(), encounterId)
                    .map(line -> syncOperationalItemFromChargeLine(item, line))
                    .orElse(false)) {
                updated++;
                continue;
            }

            long quantity =
                    item.getQuantity() == null || item.getQuantity() <= 0
                            ? 1L
                            : item.getQuantity();

            applyResolvedPricing(
                    item,
                    facilityId,
                    quantity,
                    BillingCoverageType.INSURANCE
            );
            updated++;
        }

        if (updated > 0) {
            patientServiceAndProductRepository.saveAll(items);
            patientServiceAndProductRepository.flush();
        }

        LOG.info(
                "[PSP_PRICING] Reapplied insurance plan pricing encounterId={} updatedItems={} patientInsuranceId={}",
                encounterId,
                updated,
                encounterInsuranceId
        );

        return updated;
    }

    public void applyResolvedPricing(
            PatientServiceAndProduct item,
            Long facilityId,
            long quantity
    ) {
        applyResolvedPricing(item, facilityId, quantity, null);
    }

    public void applyResolvedPricing(
            PatientServiceAndProduct item,
            Long facilityId,
            long quantity,
            BillingCoverageType coverageOverride
    ) {
        if (item == null || facilityId == null) {
            return;
        }

        if (item.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Currency is required to resolve item pricing.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        long effectiveQuantity = quantity <= 0 ? 1L : quantity;
        item.setQuantity(effectiveQuantity);

        ensureEncounterInsuranceLink(item);

        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(
                        item,
                        facilityId,
                        coverageOverride
                );

        BillingPricingInput pricingInput =
                billingPricingInputFactory.create(item, resolvedPrice);

        BillingProcessingContext context =
                BillingProcessingContext.builder()
                        .patientServiceProduct(item)
                        .pricingInput(pricingInput)
                        .build();

        billingPricingService.calculate(context);

        PriceCalculationResult pricing = context.getPricingResult();
        if (pricing == null || money(pricing.netAmount()).signum() <= 0) {
            throw new BadRequestAlertException(
                    "Item price must be a positive number greater than zero.",
                    ENTITY_NAME,
                    "price.mustBePositive"
            );
        }

        item.setUnitPrice(money(pricing.unitPrice()));
        item.setGrossAmount(money(pricing.grossAmount()));
        item.setDiscountAmount(money(pricing.discountAmount()));
        item.setExemptionAmount(money(pricing.exemptionAmount()));
        item.setTaxAmount(money(pricing.taxAmount()));
        item.setNetAmount(money(pricing.netAmount()));
        item.setTotalAmount(money(pricing.netAmount()));
        item.setCurrency(resolvedPrice.currency());
        item.setPriceSource(mapPriceSource(resolvedPrice.priceSource()));

        applyInsuranceSplit(item, pricing.netAmount(), coverageOverride);

        LOG.info(
                "[PSP_PRICING] pspId={} itemType={} priceSource={} gross={} discount={} net={} "
                        + "patientShare={} insuranceShare={} priceListId={}",
                item.getId(),
                item.getBillingItemType(),
                resolvedPrice.priceSource(),
                item.getGrossAmount(),
                item.getDiscountAmount(),
                item.getNetAmount(),
                item.getPatientShareAmount(),
                item.getInsuranceShareAmount(),
                pricingInput.priceListId()
        );
    }

    private void applyInsuranceSplit(
            PatientServiceAndProduct item,
            BigDecimal netAmount,
            BillingCoverageType coverageOverride
    ) {
        BigDecimal normalizedNet = money(netAmount);

        if (Boolean.TRUE.equals(item.getIsExempted())) {
            item.setPatientShareAmount(BigDecimal.ZERO);
            item.setInsuranceShareAmount(BigDecimal.ZERO);
            item.setRemainingAmount(BigDecimal.ZERO);
            return;
        }

        BillingCoverageType coverage = resolveCoverage(item, coverageOverride);
        if (coverage != BillingCoverageType.INSURANCE) {
            item.setPatientShareAmount(normalizedNet);
            item.setInsuranceShareAmount(BigDecimal.ZERO);
            item.setRemainingAmount(normalizedNet);
            return;
        }

        PatientInsurance insurance = resolveInsurance(item);
        if (insurance == null) {
            item.setPatientShareAmount(normalizedNet);
            item.setInsuranceShareAmount(BigDecimal.ZERO);
            item.setRemainingAmount(normalizedNet);
            LOG.warn(
                    "[PSP_PRICING] Insurance coverage without resolvable plan — treating as patient cash. pspId={} encounterId={}",
                    item.getId(),
                    item.getEncounterId()
            );
            return;
        }

        InsuranceSplit split =
                insurancePatientShareCalculator.calculateSplit(
                        insurance,
                        item,
                        normalizedNet
                );

        item.setPatientShareAmount(money(split.patientShare()));
        item.setInsuranceShareAmount(money(split.insuranceShare()));
        item.setRemainingAmount(money(split.patientShare()));
    }

    private BillingCoverageType resolveCoverage(
            PatientServiceAndProduct item,
            BillingCoverageType coverageOverride
    ) {
        if (coverageOverride != null) {
            return coverageOverride;
        }

        if (item.getPatientInsuranceId() != null) {
            return BillingCoverageType.INSURANCE;
        }

        if (item.getEncounterId() == null) {
            return BillingCoverageType.SELF_PAY;
        }

        PatientEncounter encounter =
                patientEncounterRepository.findById(item.getEncounterId()).orElse(null);

        if (encounter != null && encounter.getCoverageType() != null) {
            return encounter.getCoverageType();
        }

        if (encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(
                item.getEncounterId()
        )) {
            return BillingCoverageType.INSURANCE;
        }

        return BillingCoverageType.SELF_PAY;
    }

    private void ensureEncounterInsuranceLink(PatientServiceAndProduct item) {
        if (item.getPatientInsuranceId() != null || item.getEncounterId() == null) {
            return;
        }

        Long resolvedInsuranceId =
                encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(
                        item.getEncounterId()
                );

        if (resolvedInsuranceId != null) {
            item.setPatientInsuranceId(resolvedInsuranceId);
        }
    }

    private boolean syncOperationalItemFromChargeLine(
            PatientServiceAndProduct item,
            BillingChargeLine chargeLine
    ) {
        if (item == null || chargeLine == null) {
            return false;
        }

        BigDecimal patientShare = money(chargeLine.getPatientResponsibilityAmount());
        BigDecimal insuranceShare = money(chargeLine.getInsuranceResponsibilityAmount());

        item.setUnitPrice(money(chargeLine.getUnitPrice()));
        item.setGrossAmount(money(chargeLine.getGrossAmount()));
        item.setDiscountAmount(money(chargeLine.getDiscountAmount()));
        item.setExemptionAmount(money(chargeLine.getExemptionAmount()));
        item.setTaxAmount(money(chargeLine.getTaxAmount()));
        item.setNetAmount(money(chargeLine.getNetAmount()));
        item.setTotalAmount(money(chargeLine.getNetAmount()));
        item.setPatientShareAmount(patientShare);
        item.setInsuranceShareAmount(insuranceShare);
        item.setRemainingAmount(patientShare);

        return true;
    }

    private PatientInsurance resolveInsurance(PatientServiceAndProduct item) {
        Long patientInsuranceId = item.getPatientInsuranceId();
        if (patientInsuranceId == null && item.getEncounterId() != null) {
            patientInsuranceId =
                    encounterInsuranceEligibilityService
                            .resolveEncounterPatientInsuranceId(item.getEncounterId());
        }

        if (patientInsuranceId == null) {
            return null;
        }

        return patientInsuranceRepository.findById(patientInsuranceId).orElse(null);
    }

    private PriceSource mapPriceSource(BillingPriceSource source) {
        if (source == BillingPriceSource.PRICE_LIST) {
            return PriceSource.PRICE_LIST;
        }
        return PriceSource.DEFAULT;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
