package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ServiceClient;
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

        return calculateSplitFromRules(
                insurance,
                serviceCategory,
                serviceSource,
                normalizedNet
        );
    }

    private InsuranceSplit cashSplit(BigDecimal normalizedNet) {
        return new InsuranceSplit(normalizedNet, BigDecimal.ZERO);
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
        if (isGpOrConsultationService(serviceCategory, serviceSource)) {
            BigDecimal gpVisitCopay = insurance.getGpVisitCopay();
            if (gpVisitCopay != null && gpVisitCopay.signum() >= 0) {
                LOG.debug(
                        "[INSURANCE] Using GP visit copay patientInsuranceId={} copay={} net={}",
                        insurance.getId(),
                        gpVisitCopay,
                        normalizedNet
                );
                BigDecimal patientShare = gpVisitCopay.min(normalizedNet);
                return new InsuranceSplit(
                        patientShare,
                        money(normalizedNet.subtract(patientShare))
                );
            }
        }

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

        LOG.debug(
                "[INSURANCE] Applying benefit rule patientInsuranceId={} category={} serviceCategory={} copayPercent={} maxCopay={}",
                insurance.getId(),
                rule.benefitCategory(),
                serviceCategory,
                rule.patientCopaymentPercentage(),
                rule.patientMaximumCopayment()
        );

        return insuranceCalculationService.calculateFromBenefitRule(
                normalizedNet,
                rule,
                insurance.getMaxLimit()
        );
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

    private boolean isGpOrConsultationService(
            String serviceCategory,
            ServiceSource serviceSource
    ) {
        if (serviceSource == ServiceSource.CONSULTATION_PORTAL) {
            return true;
        }

        if (serviceCategory == null || serviceCategory.isBlank()) {
            return false;
        }

        String normalized = serviceCategory.trim().toLowerCase();
        return normalized.contains("consult")
                || normalized.contains("gp")
                || normalized.contains("general practice");
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
