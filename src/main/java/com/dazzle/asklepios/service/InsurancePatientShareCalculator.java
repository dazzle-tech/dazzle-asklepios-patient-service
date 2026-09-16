package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientInsuranceCoverage;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.InsuranceCoverageType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.PatientInsuranceCoverageRepository;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class InsurancePatientShareCalculator {

    private static final Logger LOG =
            LoggerFactory.getLogger(InsurancePatientShareCalculator.class);

    private static final String ENTITY_NAME = "insurancePatientShare";
    private static final int MONEY_SCALE = 4;

    private final InsuranceBenefitRuleService insuranceBenefitRuleService;
    private final InsuranceCalculationService insuranceCalculationService;
    private final PatientInsuranceCoverageRepository patientInsuranceCoverageRepository;
    private final ServiceClient serviceClient;
    private final PayorClient payorClient;
    private final CoverageContractShareService coverageContractShareService;

    public InsuranceSplit calculateSplit(
            PatientInsurance insurance,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal netAmount
    ) {
        return calculateSplit(
                insurance,
                null,
                serviceCategory,
                serviceSource,
                netAmount
        );
    }

    public InsuranceSplit calculateSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount
    ) {
        String serviceCategory = resolveServiceCategory(item);
        ServiceSource serviceSource =
                item == null ? null : item.getServiceSource();

        return calculateSplit(
                insurance,
                item,
                serviceCategory,
                serviceSource,
                netAmount
        );
    }

    public InsuranceSplit calculateSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal netAmount
    ) {
        BigDecimal normalizedNet = money(netAmount);

        if (normalizedNet.signum() <= 0) {
            return new InsuranceSplit(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // Cash / self-pay must never enter insurance benefit-rule calculation.
        if (insurance == null || insurance.getId() == null) {
            return cashSplit(normalizedNet);
        }

        InsuranceSplit manualOverride =
                resolveManualCoverageOverride(
                        insurance,
                        item,
                        normalizedNet
                );
        if (manualOverride != null) {
            return manualOverride;
        }

        if (item != null && serviceCategory == null) {
            serviceCategory = resolveServiceCategory(item);
        }

        if (isWaseelCoverage(insurance)) {
            InsuranceSplit waseelSplit = calculateSplitFromRules(
                    insurance,
                    serviceCategory,
                    serviceSource,
                    normalizedNet
            );
            return coverageContractShareService.capWithCoverage(
                    insurance,
                    item,
                    normalizedNet,
                    waseelSplit
            );
        }

        InsuranceSplit contractSplit =
                coverageContractShareService
                        .calculateSplit(
                                insurance,
                                item,
                                normalizedNet
                        )
                        .orElse(null);
        if (contractSplit != null) {
            return contractSplit;
        }

        return coverageContractShareService.applyLimit(
                insurance,
                item,
                normalizedNet,
                calculateSplitFromRules(
                        insurance,
                        serviceCategory,
                        serviceSource,
                        normalizedNet
                )
        );
    }

    private InsuranceSplit cashSplit(BigDecimal normalizedNet) {
        return new InsuranceSplit(normalizedNet, BigDecimal.ZERO);
    }

    public InsuranceBenefitRule resolveApplicableRule(
            PatientInsurance insurance,
            PatientServiceAndProduct item
    ) {
        if (insurance == null || insurance.getId() == null) {
            return null;
        }

        return insuranceBenefitRuleService.resolveApplicableRule(
                insurance,
                resolveServiceCategory(item),
                item == null ? null : item.getServiceSource()
        );
    }

    public BigDecimal calculatePatientShare(
            PatientInsurance insurance,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal netAmount
    ) {
        return calculateSplit(
                insurance,
                serviceCategory,
                serviceSource,
                netAmount
        ).patientShare();
    }

    private InsuranceSplit resolveManualCoverageOverride(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal normalizedNet
    ) {
        if (insurance == null
                || insurance.getId() == null
                || item == null
                || item.getBillingItemType() == null) {
            return null;
        }

        return patientInsuranceCoverageRepository
                .findFirstByInsurance_IdAndItemType(
                        insurance.getId(),
                        item.getBillingItemType()
                )
                .map(coverage -> applyManualCoverage(coverage, normalizedNet))
                .orElse(null);
    }

    private InsuranceSplit applyManualCoverage(
            PatientInsuranceCoverage coverage,
            BigDecimal normalizedNet
    ) {
        BigDecimal patientShare;

        if (coverage.getCoverageType() == InsuranceCoverageType.FIXED) {
            patientShare = money(coverage.getAmount()).min(normalizedNet);
        } else {
            patientShare =
                    insuranceCalculationService
                            .calculateSplit(
                                    normalizedNet,
                                    coverage.getAmount(),
                                    BigDecimal.ZERO
                            )
                            .patientShare();
        }

        return new InsuranceSplit(
                patientShare,
                money(normalizedNet.subtract(patientShare))
        );
    }

    private InsuranceSplit calculateSplitFromRules(
            PatientInsurance insurance,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal normalizedNet
    ) {
        InsuranceBenefitRule rule =
                insuranceBenefitRuleService.resolveApplicableRule(
                        insurance,
                        serviceCategory,
                        serviceSource
                );

        if (rule == null) {
            throw new BadRequestAlertException(
                    "No insurance benefit rule found for this service.",
                    ENTITY_NAME,
                    "insurance.benefit.rule.missing"
            );
        }

        if (rule.patientCopaymentPercentage() == null
                && rule.patientMaximumCopayment() == null) {
            throw new BadRequestAlertException(
                    "Matched insurance benefit rule has no copayment values.",
                    ENTITY_NAME,
                    "insurance.benefit.rule.incomplete"
            );
        }

        LOG.debug(
                "[INSURANCE] Applying benefit rule patientInsuranceId={} category={} item={} network={} serviceCategory={} copayPercent={} maxCopay={} maxBenefit={}",
                insurance.getId(),
                rule.benefitCategory(),
                rule.itemName(),
                rule.networkType(),
                serviceCategory,
                rule.patientCopaymentPercentage(),
                rule.patientMaximumCopayment(),
                rule.maximumBenefit()
        );

        return insuranceCalculationService.calculateFromBenefitRule(
                normalizedNet,
                rule,
                resolvePolicyMaximumLimit(insurance, rule)
        );
    }

    private BigDecimal resolvePolicyMaximumLimit(
            PatientInsurance insurance,
            InsuranceBenefitRule rule
    ) {
        if (insurance == null) {
            return null;
        }

        BigDecimal maxLimit = insurance.getMaxLimit();
        if (maxLimit == null || maxLimit.signum() <= 0) {
            return null;
        }

        BigDecimal copayCap = rule != null && rule.patientMaximumCopayment() != null
                ? rule.patientMaximumCopayment()
                : firstPositive(insurance.getDefaultMaximumCopayment());

        /*
         * CCHI maxLimit is the patient copay cap, not an insurance annual cap.
         * Keep it as an insurance-side policy limit only when it is strictly
         * larger than the patient copay maximum.
         */
        if (copayCap != null && maxLimit.compareTo(copayCap) <= 0) {
            return null;
        }

        return maxLimit;
    }

    private String resolveServiceCategory(PatientServiceAndProduct item) {
        if (item == null
                || item.getBillingItemType() != BillingItemTypes.SERVICE
                || item.getServiceId() == null) {
            return null;
        }

        try {
            ServiceSetupDTO service =
                    serviceClient.getServiceDetails(item.getServiceId());
            return service == null ? null : service.category();
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[INSURANCE] Unable to resolve service category serviceId={}",
                    item.getServiceId(),
                    exception
            );
            return null;
        }
    }

    private boolean isWaseelCoverage(PatientInsurance insurance) {
        if (insurance == null || insurance.getPayorId() == null) {
            return false;
        }

        try {
            PayorDTO payor = payorClient.getPayorById(insurance.getPayorId());
            return payor != null && Boolean.TRUE.equals(payor.isWaseelEnabled());
        } catch (FeignException exception) {
            LOG.warn(
                    "[INSURANCE] Unable to resolve payor waseel flag payorId={}. Keeping existing share calculation.",
                    insurance.getPayorId(),
                    exception
            );
            return true;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[INSURANCE] Payor lookup failed payorId={}. Keeping existing share calculation.",
                    insurance.getPayorId(),
                    exception
            );
            return true;
        }
    }

    private BigDecimal firstPositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return null;
        }

        return value;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
