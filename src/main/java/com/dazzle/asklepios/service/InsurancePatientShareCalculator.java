package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientInsuranceCoverage;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.InsuranceCoverageType;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.PatientInsuranceCoverageRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.helper.PayorHelper;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.eligibility.EligibilityContractReader;
import com.dazzle.asklepios.service.eligibility.EligibilityContractRules;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InsurancePatientShareCalculator {

    static final String MANUAL_COVERAGE = "MANUAL_COVERAGE";
    private static final Logger LOG =
            LoggerFactory.getLogger(InsurancePatientShareCalculator.class);

    private static final String ENTITY_NAME = "insurancePatientShare";
    private static final int MONEY_SCALE = 4;

    private final InsuranceBenefitRuleService insuranceBenefitRuleService;
    private final InsuranceCalculationService insuranceCalculationService;
    private final PatientInsuranceCoverageRepository patientInsuranceCoverageRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ServiceClient serviceClient;
    private final PayorHelper payorHelper;
    private final CoverageContractShareService coverageContractShareService;
    private final EligibilityContractReader eligibilityContractReader;

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
                netAmount,
                null
        );
    }

    public InsuranceSplit calculateSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount,
            VisitMaxLimitTracker visitMaxLimitTracker
    ) {
        String serviceCategory = resolveServiceCategory(item);
        ServiceSource serviceSource =
                item == null ? null : item.getServiceSource();

        return calculateSplit(
                insurance,
                item,
                serviceCategory,
                serviceSource,
                netAmount,
                visitMaxLimitTracker
        );
    }

    public InsuranceSplit calculateSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal netAmount
    ) {
        return calculateSplit(
                insurance,
                item,
                serviceCategory,
                serviceSource,
                netAmount,
                null
        );
    }

    public InsuranceSplit calculateSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal netAmount,
            VisitMaxLimitTracker visitMaxLimitTracker
    ) {
        BigDecimal normalizedNet = money(netAmount);

        if (normalizedNet.signum() <= 0) {
            return new InsuranceSplit(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // Cash / self-pay must never enter insurance benefit-rule calculation.
        if (insurance == null || insurance.getId() == null) {
            return cashSplit(normalizedNet);
        }

        if (!isLatestCoverageInForce(insurance)) {
            LOG.warn(
                    "[INSURANCE] Coverage is not in-force; billing as cash "
                            + "patientInsuranceId={} net={}",
                    insurance.getId(),
                    normalizedNet
            );
            return cashSplit(normalizedNet);
        }

        InsuranceSplit split = resolveCoverageSplit(
                insurance,
                item,
                serviceCategory,
                serviceSource,
                normalizedNet
        );

        VisitMaxLimitTracker tracker =
                visitMaxLimitTracker != null
                        ? visitMaxLimitTracker
                        : trackerForEncounter(insurance, item);

        BigDecimal billedNet = billedNetOf(split, normalizedNet);
        InsuranceSplit capped = tracker.consume(split, billedNet);
        if (tracker.hasLimit()
                && capped.patientShare().compareTo(split.patientShare()) != 0) {
            LOG.info(
                    "[INSURANCE] Visit max-limit applied encounterId={} "
                            + "patientInsuranceId={} requestedPatient={} "
                            + "cappedPatient={} remainingVisitMax={}",
                    item == null ? null : item.getEncounterId(),
                    insurance.getId(),
                    split.patientShare(),
                    capped.patientShare(),
                    tracker.remaining()
            );
        }

        return capped;
    }

    public VisitMaxLimitTracker createVisitMaxLimitTracker(
            PatientInsurance insurance
    ) {
        return createVisitMaxLimitTracker(insurance, BigDecimal.ZERO);
    }

    public VisitMaxLimitTracker createVisitMaxLimitTracker(
            PatientInsurance insurance,
            BigDecimal alreadyConsumedPatientShare
    ) {
        return VisitMaxLimitTracker.withConsumed(
                resolveVisitMaxLimit(insurance),
                alreadyConsumedPatientShare
        );
    }

    /** Why the whole net is on the patient; a manual coverage on the insurance overrides the contract. */
    public String contractCashReason(PatientInsurance insurance, PatientServiceAndProduct item) {
        boolean manualCoverage = item != null
                && item.getBillingItemType() != null
                && patientInsuranceCoverageRepository
                        .findFirstByInsurance_IdAndItemType(insurance.getId(), item.getBillingItemType())
                        .isPresent();
        return manualCoverage
                ? MANUAL_COVERAGE
                : coverageContractShareService.contractCashReason(insurance, item);
    }

    public boolean usesNphiesContractRules(PatientInsurance insurance) {
        return coverageContractShareService.usesNphiesApprovalCoverage(insurance);
    }

    public boolean isLatestCoverageInForce(PatientInsurance insurance) {
        if (insurance != null && usesNphiesContractRules(insurance)) {
            boolean inForce = insurance.getExpirationDate() != null
                    && !insurance.getExpirationDate().isBefore(LocalDate.now());
            LOG.info(
                    "[INSURANCE] NPHIES approval coverage uses the coverage contract on the visit "
                            + "patientInsuranceId={} expirationInForce={}",
                    insurance.getId(),
                    inForce
            );
            return inForce;
        }

        return insuranceBenefitRuleService.isLatestCoverageInForce(insurance);
    }

    private InsuranceSplit cashSplit(BigDecimal normalizedNet) {
        return new InsuranceSplit(normalizedNet, BigDecimal.ZERO);
    }

    private InsuranceSplit resolveCoverageSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal normalizedNet
    ) {
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

        boolean nphiesContractRules =
                coverageContractShareService.usesNphiesApprovalCoverage(insurance, item);

        if (isWaseelCoverage(insurance) && !nphiesContractRules) {
            Optional<InsuranceSplit> eligibilityContractSplit =
                    eligibilityContractSplit(insurance, item, normalizedNet);
            if (eligibilityContractSplit.isPresent()) {
                return coverageContractShareService.capWithCoverage(
                        insurance,
                        item,
                        normalizedNet,
                        eligibilityContractSplit.get()
                );
            }

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

        if (nphiesContractRules) {
            LOG.info(
                    "[INSURANCE] NPHIES coverage contract has no copayment or coverage reading "
                            + "for this item. Wasel benefit rules are not used. patientInsuranceId={}",
                    insurance.getId()
            );
            return coverageContractShareService.applyLimit(
                    insurance,
                    item,
                    normalizedNet,
                    new InsuranceSplit(
                            normalizedNet,
                            BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                    )
            );
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

    /**
     * Patient share from the patient-level eligibility contract: the copayment of the line scope
     * (dental, medicines, outpatient, policy) in the visit network, e.g. 20% with at most 75 per service.
     * Empty when the insurance has no contract in force yet; the older benefit rules then apply.
     */
    private Optional<InsuranceSplit> eligibilityContractSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal normalizedNet
    ) {
        Optional<InsuranceBenefitRule> copayment = EligibilityContractRules.copaymentFor(
                eligibilityContractReader.activeRules(insurance),
                EligibilityContractRules.scopeOf(item),
                EligibilityContractRules.visitNetwork(insurance.getSiteEligibility())
        );
        if (copayment.isEmpty()) {
            return Optional.empty();
        }

        LOG.debug(
                "[INSURANCE] Eligibility contract copayment patientInsuranceId={} benefit=\"{}\" network={} percent={} maximum={}",
                insurance.getId(),
                copayment.get().itemName(),
                copayment.get().networkType(),
                copayment.get().patientCopaymentPercentage(),
                copayment.get().patientMaximumCopayment()
        );
        return Optional.of(insuranceCalculationService.calculateSplit(
                normalizedNet,
                copayment.get().patientCopaymentPercentage(),
                copayment.get().patientMaximumCopayment()
        ));
    }

    private BigDecimal billedNetOf(InsuranceSplit split, BigDecimal fallbackNet) {
        if (split == null) {
            return fallbackNet;
        }

        BigDecimal billedNet = money(split.patientShare().add(split.insuranceShare()));
        if (billedNet.signum() <= 0) {
            return fallbackNet;
        }

        return billedNet;
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

        InsuranceBenefitRule ruleForService =
                visitLevelMaxEqualsPerServiceCap(insurance, rule)
                        ? withoutPerServiceCopayCap(rule)
                        : rule;

        return insuranceCalculationService.calculateFromBenefitRule(
                normalizedNet,
                ruleForService,
                null
        );
    }

    private VisitMaxLimitTracker trackerForEncounter(
            PatientInsurance insurance,
            PatientServiceAndProduct item
    ) {
        BigDecimal visitMax = resolveVisitMaxLimit(insurance);
        if (visitMax == null) {
            return VisitMaxLimitTracker.unbounded();
        }

        Long encounterId = item == null ? null : item.getEncounterId();
        Long excludeItemId = item == null ? null : item.getId();
        BigDecimal consumed =
                consumedVisitPatientShare(
                        insurance,
                        encounterId,
                        excludeItemId
                );

        return VisitMaxLimitTracker.withConsumed(visitMax, consumed);
    }

    /**
     * CCHI / insurance maxLimit is a visit pool, not a per-service cap.
     */
    private BigDecimal resolveVisitMaxLimit(PatientInsurance insurance) {
        if (insurance == null) {
            return null;
        }

        return firstPositive(insurance.getMaxLimit());
    }

    private boolean visitLevelMaxEqualsPerServiceCap(
            PatientInsurance insurance,
            InsuranceBenefitRule rule
    ) {
        BigDecimal visitMax = resolveVisitMaxLimit(insurance);
        if (visitMax == null
                || rule == null
                || rule.patientMaximumCopayment() == null) {
            return false;
        }

        return visitMax.compareTo(rule.patientMaximumCopayment()) == 0;
    }

    private InsuranceBenefitRule withoutPerServiceCopayCap(
            InsuranceBenefitRule rule
    ) {
        return new InsuranceBenefitRule(
                rule.id(),
                rule.benefitCategory(),
                rule.itemName(),
                rule.itemCode(),
                rule.networkType(),
                rule.providerType(),
                rule.term(),
                rule.unit(),
                rule.currency(),
                rule.maximumBenefit(),
                rule.approvalLimit(),
                rule.patientCopaymentPercentage(),
                null,
                rule.globalDefault(),
                rule.exceptionsJson()
        );
    }

    private BigDecimal consumedVisitPatientShare(
            PatientInsurance insurance,
            Long encounterId,
            Long excludeItemId
    ) {
        if (insurance == null
                || insurance.getId() == null
                || encounterId == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal consumed = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        for (PatientServiceAndProduct existing :
                patientServiceAndProductRepository.findByEncounterId(encounterId)) {
            if (existing == null
                    || existing.getId() == null
                    || existing.getId().equals(excludeItemId)) {
                continue;
            }

            if (!insurance.getId().equals(existing.getPatientInsuranceId())) {
                continue;
            }

            if (Boolean.TRUE.equals(existing.getIsExempted())
                    || existing.isUncoveredCashItem()
                    || existing.getPaymentStatus() == PaymentStatus.CANCELLED) {
                continue;
            }

            consumed = consumed.add(money(existing.getPatientShareAmount()));
        }

        return consumed;
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

    public boolean isWaseelCoverage(PatientInsurance insurance) {
        if (insurance == null
                || (insurance.getPayorId() == null
                && (insurance.getPayerNphiesId() == null || insurance.getPayerNphiesId().isBlank()))) {
            return false;
        }

        try {
            PayorDTO payor = payorHelper.findPayor(insurance.getPayorId(), insurance.getPayerNphiesId());
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
