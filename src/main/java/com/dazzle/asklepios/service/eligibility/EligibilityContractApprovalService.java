package com.dazzle.asklepios.service.eligibility;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityBenefitScope;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityLimitType;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityNetworkType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.event.EncounterPreAuthorizationSyncEvent;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.CoverageContractShareService;
import com.dazzle.asklepios.service.InsurancePatientShareCalculator;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.service.eligibility.EligibilityContractRules.ContractLimit;
import com.dazzle.asklepios.service.eligibility.EligibilityContractRules.LimitBreach;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies the limits of the patient-level eligibility contract to visit lines, the way Coverage
 * Management applies a contract pre-approval limit: a line that crosses a limit is held off the
 * patient account and sent to Waseel as a pre-authorization.
 *
 * <p>Only for a Waseel payer without an NPHIES coverage contract, with an eligibility contract in force:
 * the benefit rules stored from the latest eligibility check ({@link EligibilityContractReader}).
 * Default services of a dental visit travel with the dental pre-authorization, but never use the dental limit.
 */
@Service
@RequiredArgsConstructor
public class EligibilityContractApprovalService {

    private static final Logger LOG = LoggerFactory.getLogger(EligibilityContractApprovalService.class);
    private static final ZoneId PAYER_ZONE = ZoneId.of("Asia/Riyadh");
    private static final int MONEY_SCALE = 4;

    private final EligibilityContractReader contractReader;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;
    private final CoverageContractShareService coverageContractShareService;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Billing, once the price is known: holds the line for a Waseel pre-authorization when it crosses
     * a contract limit. Returns true when the line was held.
     */
    public boolean holdIfExceeded(BillingProcessingContext context) {
        PatientServiceAndProduct line = context == null ? null : context.getPatientServiceProduct();
        if (line == null || !isOpenForEvaluation(line)) {
            return false;
        }

        PatientInsurance insurance = resolveInsurance(line);
        List<PatientServiceAndProduct> visitLines = visitLines(line);
        BigDecimal lineAmount = money(context.getNetAmount());
        Optional<LimitBreach> breach = evaluate(insurance, line, lineAmount, visitLines);
        if (breach.isEmpty()) {
            return false;
        }

        hold(line, insurance, context.getPricingResult());
        if (EligibilityContractRules.isDentalTreatment(line)) {
            joinVisitDefaultServices(line, insurance, visitLines);
        }
        applicationEventPublisher.publishEvent(new EncounterPreAuthorizationSyncEvent(line.getEncounterId()));

        LOG.info(
                "[ELIGIBILITY_CONTRACT] Line held for Waseel pre-authorization. encounterId={} pspId={} amount={} used={} limit={} {} reason=\"{}\"",
                line.getEncounterId(),
                line.getId(),
                lineAmount,
                breach.get().amountUsed(),
                breach.get().limit().limitType(),
                breach.get().limit().limitValue(),
                breach.get().reason()
        );
        return true;
    }

    /**
     * Encounter pre-authorization sync: true when the contract requires this line to be pre-authorized,
     * so the sync keeps the hold instead of resetting it from the price list.
     */
    public boolean requiresPreAuthorization(PatientServiceAndProduct line, List<PatientServiceAndProduct> visitLines) {
        if (line == null || line.getPatientInsuranceId() == null) {
            return false;
        }
        PatientInsurance insurance = patientInsuranceRepository.findById(line.getPatientInsuranceId()).orElse(null);
        return evaluate(insurance, line, money(line.getNetAmount()), visitLines).isPresent()
                || joinsDentalPreAuthorization(line, insurance, visitLines);
    }

    /** Pricing preview: true when billing this line would hold it for a Waseel pre-authorization. */
    public boolean requiresPreAuthorization(PatientInsurance insurance, PatientServiceAndProduct line, BigDecimal lineAmount) {
        return line != null && evaluate(insurance, line, money(lineAmount), visitLines(line)).isPresent();
    }

    /** The contract limit the line crosses, if the insurance bills from its eligibility contract. */
    public Optional<LimitBreach> evaluate(
            PatientInsurance insurance,
            PatientServiceAndProduct line,
            BigDecimal lineAmount,
            List<PatientServiceAndProduct> visitLines
    ) {
        List<InsuranceBenefitRule> rules = applicableRules(insurance, line);
        if (rules.isEmpty()) {
            return Optional.empty();
        }

        EligibilityBenefitScope scope = EligibilityContractRules.scopeOf(line);
        EligibilityNetworkType network = EligibilityContractRules.visitNetwork(insurance.getSiteEligibility());
        List<ContractLimit> limits = EligibilityContractRules.limitsFor(rules, scope, network);
        if (limits.isEmpty()) {
            return Optional.empty();
        }

        Map<ContractLimit, BigDecimal> usage = new HashMap<>();
        return EligibilityContractRules.firstBreach(
                limits,
                lineAmount,
                limit -> limit.limitType() == EligibilityLimitType.MAXIMUM_BENEFIT
                        ? usage.computeIfAbsent(limit, key -> amountUsed(key, insurance, line, visitLines))
                        : BigDecimal.ZERO
        );
    }

    /** The eligibility contract rules when they bill this line: Waseel payer, no NPHIES contract, in force. */
    public List<InsuranceBenefitRule> applicableRules(PatientInsurance insurance, PatientServiceAndProduct line) {
        if (insurance == null || insurance.getId() == null) {
            return List.of();
        }
        try {
            if (!insurancePatientShareCalculator.isWaseelCoverage(insurance)
                    || coverageContractShareService.usesNphiesApprovalCoverage(insurance, line)) {
                return List.of();
            }
        } catch (RuntimeException exception) {
            LOG.warn("[ELIGIBILITY_CONTRACT] Contract routing could not be read patientInsuranceId={}", insurance.getId(), exception);
            return List.of();
        }
        return contractReader.activeRules(insurance);
    }

    /**
     * What the payer already owes within the period of the limit, for the lines that use it
     * (a dental limit counts dental treatment only).
     */
    private BigDecimal amountUsed(
            ContractLimit limit,
            PatientInsurance insurance,
            PatientServiceAndProduct line,
            List<PatientServiceAndProduct> visitLines
    ) {
        LocalDate start = EligibilityContractRules.periodStart(
                limit.periodBasis(), insurance.getBenefitStartDate(), LocalDate.now(PAYER_ZONE));
        List<PatientServiceAndProduct> periodLines = start == null
                ? visitLines
                : patientServiceAndProductRepository.findByPatientInsuranceIdAndCreatedDateGreaterThanEqual(
                        insurance.getId(), start.atStartOfDay(PAYER_ZONE).toInstant());

        return periodLines.stream()
                .filter(other -> isOwedByPayer(other, line, insurance))
                .filter(other -> EligibilityContractRules.countsToward(limit, other))
                .map(EligibilityContractApprovalService::payerAmount)
                .reduce(money(BigDecimal.ZERO), BigDecimal::add);
    }

    private static boolean isOwedByPayer(PatientServiceAndProduct other, PatientServiceAndProduct line, PatientInsurance insurance) {
        return other != null
                && other.getId() != null
                && !Objects.equals(other.getId(), line.getId())
                && Objects.equals(other.getPatientInsuranceId(), insurance.getId())
                && !other.isUncoveredCashItem()
                && !Boolean.TRUE.equals(other.getIsExempted())
                && other.getPaymentStatus() != PaymentStatus.CANCELLED
                && other.getPreAuthorizationStatus() != PreAuthorizationStatus.REJECTED
                && other.getPreAuthorizationStatus() != PreAuthorizationStatus.CANCELLED;
    }

    /** The insurance share once the line is split; a held line has no split yet, so its net counts. */
    private static BigDecimal payerAmount(PatientServiceAndProduct line) {
        BigDecimal insuranceShare = money(line.getInsuranceShareAmount());
        BigDecimal patientShare = money(line.getPatientShareAmount());
        return insuranceShare.signum() > 0 || patientShare.signum() > 0 ? insuranceShare : money(line.getNetAmount());
    }

    /** Default services of a dental visit join a dental pre-authorization that is not sent yet. */
    static boolean joinsDentalPreAuthorization(
            PatientServiceAndProduct line,
            PatientInsurance insurance,
            List<PatientServiceAndProduct> visitLines
    ) {
        if (insurance == null || visitLines == null || !isOpenDefaultService(line, insurance)) {
            return false;
        }
        return visitLines.stream().anyMatch(other -> isDentalTreatmentWaitingForWaseel(other, insurance));
    }

    private void joinVisitDefaultServices(
            PatientServiceAndProduct dentalLine,
            PatientInsurance insurance,
            List<PatientServiceAndProduct> visitLines
    ) {
        List<PatientServiceAndProduct> joined = visitLines.stream()
                .filter(other -> !Objects.equals(other.getId(), dentalLine.getId()))
                .filter(other -> isOpenDefaultService(other, insurance))
                .toList();
        joined.forEach(defaultService -> {
            defaultService.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
            defaultService.setPreAuthorizationRequired(true);
            defaultService.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        });
        if (!joined.isEmpty()) {
            patientServiceAndProductRepository.saveAll(joined);
            LOG.info(
                    "[ELIGIBILITY_CONTRACT] Default services join the dental pre-authorization. encounterId={} pspIds={}",
                    dentalLine.getEncounterId(),
                    joined.stream().map(PatientServiceAndProduct::getId).toList()
            );
        }
    }

    /** A default service of the visit not collected yet, with no request of its own. */
    private static boolean isOpenDefaultService(PatientServiceAndProduct line, PatientInsurance insurance) {
        return EligibilityContractRules.isDefaultService(line)
                && Objects.equals(line.getPatientInsuranceId(), insurance.getId())
                && !Boolean.TRUE.equals(line.getIsBilled())
                && !line.isUncoveredCashItem()
                && !Boolean.TRUE.equals(line.getIsExempted())
                && line.getPreAuthorizationRequestId() == null
                && (line.getPaymentStatus() == null
                        || line.getPaymentStatus() == PaymentStatus.PENDING
                        || line.getPaymentStatus() == PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
    }

    private static boolean isDentalTreatmentWaitingForWaseel(PatientServiceAndProduct line, PatientInsurance insurance) {
        return EligibilityContractRules.isDentalTreatment(line)
                && Objects.equals(line.getPatientInsuranceId(), insurance.getId())
                && line.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL
                && line.getPreAuthorizationRequestId() == null
                && line.getPaymentStatus() != PaymentStatus.CANCELLED;
    }

    private static boolean isOpenForEvaluation(PatientServiceAndProduct line) {
        if (line.getId() == null
                || line.getEncounterId() == null
                || line.isUncoveredCashItem()
                || Boolean.TRUE.equals(line.getIsExempted())
                || line.getPaymentStatus() == PaymentStatus.CANCELLED
                || line.getPreAuthorizationRequestId() != null) {
            return false;
        }
        PreAuthorizationStatus status = line.getPreAuthorizationStatus();
        return status == null || status == PreAuthorizationStatus.NOT_REQUIRED;
    }

    /** The line waits off the patient account; its price is kept for the request and for the next lines. */
    private void hold(PatientServiceAndProduct line, PatientInsurance insurance, PriceCalculationResult pricing) {
        line.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
        line.setPreAuthorizationRequired(true);
        line.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        if (line.getPatientInsuranceId() == null) {
            line.setPatientInsuranceId(insurance.getId());
        }
        if (pricing != null) {
            line.setGrossAmount(pricing.grossAmount());
            line.setNetAmount(pricing.netAmount());
        }
        patientServiceAndProductRepository.save(line);
    }

    private List<PatientServiceAndProduct> visitLines(PatientServiceAndProduct line) {
        return line.getEncounterId() == null ? List.of() : patientServiceAndProductRepository.findByEncounterId(line.getEncounterId());
    }

    private PatientInsurance resolveInsurance(PatientServiceAndProduct line) {
        Long insuranceId = line.getPatientInsuranceId();
        if (insuranceId == null) {
            insuranceId = patientEncounterRepository.findById(line.getEncounterId())
                    .map(PatientEncounter::getPatientInsuranceId)
                    .orElse(null);
        }
        return insuranceId == null ? null : patientInsuranceRepository.findById(insuranceId).orElse(null);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
