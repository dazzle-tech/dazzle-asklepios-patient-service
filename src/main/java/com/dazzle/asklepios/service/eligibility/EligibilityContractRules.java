package com.dazzle.asklepios.service.eligibility;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityBenefitScope;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityLimitType;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityNetworkType;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityPeriodBasis;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * How the patient-level eligibility contract applies to one visit line. The contract is the benefit
 * rules stored from the latest eligibility check (patient_insurance_benefit_rules); one rule row is one
 * benefit of the response with its copayment, approval limit, maximum benefit, network and term.
 * Pure: the caller loads the rules and the amounts already used.
 *
 * <ul>
 *   <li>Scope of the line: dental treatment is DENTAL, medicines are MEDICATION, every other line
 *       (default clinic services included) is OUTPATIENT for the patient share and POLICY for limits.</li>
 *   <li>Patient share: the copayment of the line scope, else the outpatient one, else the policy one,
 *       in the visit network.</li>
 *   <li>Limits: those of the line scope and the policy limits. A line above an approval limit, or a line
 *       that takes the amount used in the period above a maximum benefit, needs pre-authorization.</li>
 * </ul>
 */
public final class EligibilityContractRules {

    private static final Set<ServiceSource> DENTAL_SOURCES = EnumSet.of(
            ServiceSource.DENTAL_PROCEDURE,
            ServiceSource.DENTAL_CHART_CDT_TREATMENT,
            ServiceSource.DENTAL_CHART_MANUAL_SERVICE
    );

    private static final String COST_BENEFICIARY_CATEGORY = "cost beneficiary";

    private EligibilityContractRules() {
    }

    /** One limit of the contract, read from a benefit rule. */
    public record ContractLimit(
            EligibilityBenefitScope scope,
            EligibilityLimitType limitType,
            EligibilityNetworkType networkType,
            String benefitName,
            BigDecimal limitValue,
            EligibilityPeriodBasis periodBasis
    ) {
    }

    /** A limit the line crosses, with what decided it. */
    public record LimitBreach(ContractLimit limit, BigDecimal lineAmount, BigDecimal amountUsed) {

        public String reason() {
            return limit.limitType() == EligibilityLimitType.APPROVAL_LIMIT
                    ? "Eligibility approval limit exceeded: " + limit.benefitName()
                    : "Eligibility maximum benefit exceeded: " + limit.benefitName();
        }
    }

    // ---- visit lines ----

    /** Dental treatment: ordered from the dental chart or as a dental procedure. */
    public static boolean isDentalTreatment(PatientServiceAndProduct line) {
        return line != null && DENTAL_SOURCES.contains(line.getServiceSource());
    }

    /** A default service of the visit (consultation, fees): never dental treatment, whatever the clinic. */
    public static boolean isDefaultService(PatientServiceAndProduct line) {
        return line != null && line.getServiceSource() == ServiceSource.ENCOUNTER_DEFAULT_SERVICE;
    }

    public static EligibilityBenefitScope scopeOf(PatientServiceAndProduct line) {
        if (isDentalTreatment(line)) {
            return EligibilityBenefitScope.DENTAL;
        }
        if (line != null && (line.getBillingItemType() == BillingItemTypes.MEDICATION
                || line.getServiceSource() == ServiceSource.PRESCRIPTION)) {
            return EligibilityBenefitScope.MEDICATION;
        }
        return EligibilityBenefitScope.OUTPATIENT;
    }

    /** Out of network only when the site eligibility says so. */
    public static EligibilityNetworkType visitNetwork(String siteEligibility) {
        String text = siteEligibility == null ? "" : siteEligibility.toLowerCase(Locale.ROOT);
        return text.contains("out") && text.contains("network")
                ? EligibilityNetworkType.OUT_OF_NETWORK
                : EligibilityNetworkType.IN_NETWORK;
    }

    // ---- benefit rules ----

    /** Which services a benefit rule covers, from its category and name as the payer wrote them. */
    public static EligibilityBenefitScope scopeOf(InsuranceBenefitRule rule) {
        String category = nullToEmpty(rule.benefitCategory()).toLowerCase(Locale.ROOT);
        String text = (category + " " + nullToEmpty(rule.itemName())).toLowerCase(Locale.ROOT);
        if (category.startsWith(COST_BENEFICIARY_CATEGORY)) {
            return EligibilityBenefitScope.POLICY;
        }
        if (text.contains("dental")) {
            return EligibilityBenefitScope.DENTAL;
        }
        if (text.contains("vision") || text.contains("optical") || text.contains("spectacle")) {
            return EligibilityBenefitScope.VISION;
        }
        if (text.contains("medicine") || text.contains("medication") || text.contains("drug")
                || text.contains("prescribed") || text.contains("pharmac")) {
            return EligibilityBenefitScope.MEDICATION;
        }
        if (text.contains("maternity") || text.contains("pregnan")) {
            return EligibilityBenefitScope.MATERNITY;
        }
        if (text.contains("inpatient") || text.contains("room and board")) {
            return EligibilityBenefitScope.INPATIENT;
        }
        if (text.contains("outpatient")) {
            return EligibilityBenefitScope.OUTPATIENT;
        }
        if (text.contains("policy") || text.contains("health benefit plan")) {
            return EligibilityBenefitScope.POLICY;
        }
        return EligibilityBenefitScope.OTHER;
    }

    /** {@code null} when the rule does not name a network (it then applies in and out of network). */
    public static EligibilityNetworkType networkOf(InsuranceBenefitRule rule) {
        String network = rule.networkType() == null ? "" : rule.networkType().trim().toUpperCase(Locale.ROOT);
        return switch (network) {
            case "IN_NETWORK" -> EligibilityNetworkType.IN_NETWORK;
            case "OUT_OF_NETWORK" -> EligibilityNetworkType.OUT_OF_NETWORK;
            default -> EligibilityNetworkType.fromText(rule.networkType());
        };
    }

    /** The copayment rule of the line scope, falling back to outpatient, then the policy. */
    public static Optional<InsuranceBenefitRule> copaymentFor(
            List<InsuranceBenefitRule> rules,
            EligibilityBenefitScope scope,
            EligibilityNetworkType network
    ) {
        List<InsuranceBenefitRule> copayments = contractRows(rules).stream()
                .filter(EligibilityContractRules::hasCopayment)
                .toList();
        for (EligibilityBenefitScope candidate : List.of(scope, EligibilityBenefitScope.OUTPATIENT, EligibilityBenefitScope.POLICY)) {
            Optional<InsuranceBenefitRule> row = inNetwork(copayments, candidate, network);
            if (row.isPresent()) {
                return row;
            }
        }
        return Optional.empty();
    }

    /** The limits that apply to the line: those of its own scope, and the policy limits. */
    public static List<ContractLimit> limitsFor(
            List<InsuranceBenefitRule> rules,
            EligibilityBenefitScope scope,
            EligibilityNetworkType network
    ) {
        EligibilityBenefitScope limitScope = scope == EligibilityBenefitScope.OUTPATIENT ? EligibilityBenefitScope.POLICY : scope;
        List<ContractLimit> limits = new ArrayList<>();
        for (InsuranceBenefitRule rule : contractRows(rules)) {
            EligibilityBenefitScope ruleScope = scopeOf(rule);
            EligibilityNetworkType ruleNetwork = networkOf(rule);
            boolean applies = (ruleScope == limitScope || ruleScope == EligibilityBenefitScope.POLICY)
                    && (ruleNetwork == null || ruleNetwork == network);
            if (!applies) {
                continue;
            }
            EligibilityPeriodBasis period = EligibilityPeriodBasis.fromTerm(rule.term());
            if (isPositive(rule.approvalLimit())) {
                limits.add(new ContractLimit(ruleScope, EligibilityLimitType.APPROVAL_LIMIT, ruleNetwork,
                        rule.itemName(), rule.approvalLimit(), period));
            }
            if (isPositive(rule.maximumBenefit())) {
                limits.add(new ContractLimit(ruleScope, EligibilityLimitType.MAXIMUM_BENEFIT, ruleNetwork,
                        rule.itemName(), rule.maximumBenefit(), period));
            }
        }
        return limits;
    }

    /**
     * The first limit the line crosses.
     *
     * @param amountUsed what the payer already owes within the period of the given limit
     */
    public static Optional<LimitBreach> firstBreach(
            List<ContractLimit> limits,
            BigDecimal lineAmount,
            Function<ContractLimit, BigDecimal> amountUsed
    ) {
        BigDecimal line = zeroIfNull(lineAmount);
        for (ContractLimit limit : limits) {
            if (limit.limitType() == EligibilityLimitType.APPROVAL_LIMIT) {
                if (line.compareTo(limit.limitValue()) > 0) {
                    return Optional.of(new LimitBreach(limit, line, BigDecimal.ZERO));
                }
                continue;
            }
            BigDecimal used = zeroIfNull(amountUsed.apply(limit));
            if ((line.signum() > 0 || used.signum() > 0) && used.add(line).compareTo(limit.limitValue()) > 0) {
                return Optional.of(new LimitBreach(limit, line, used));
            }
        }
        return Optional.empty();
    }

    /**
     * First day of the current period of a limit, or {@code null} for a per-visit limit.
     * A policy year renews on the anniversary of the benefit start date.
     */
    public static LocalDate periodStart(EligibilityPeriodBasis period, LocalDate benefitStartDate, LocalDate today) {
        if (period == null || period == EligibilityPeriodBasis.PER_ENCOUNTER) {
            return null;
        }
        return switch (period) {
            case PER_DAY -> today;
            case POLICY_TERM -> benefitStartDate == null ? LocalDate.of(today.getYear(), 1, 1) : benefitStartDate;
            case POLICY_YEAR -> {
                if (benefitStartDate == null || benefitStartDate.isAfter(today)) {
                    yield LocalDate.of(today.getYear(), 1, 1);
                }
                LocalDate anniversary = benefitStartDate.withYear(today.getYear());
                yield anniversary.isAfter(today) ? anniversary.minusYears(1) : anniversary;
            }
            default -> null;
        };
    }

    /**
     * Whether another line of the period counts toward a limit: a dental limit counts dental treatment
     * only (default services of a dental visit do not use it); the policy limit counts every line.
     */
    public static boolean countsToward(ContractLimit limit, PatientServiceAndProduct other) {
        return switch (limit.scope()) {
            case POLICY -> true;
            case DENTAL -> isDentalTreatment(other);
            default -> scopeOf(other) == limit.scope();
        };
    }

    /** The rows the payer returned; the derived global default only repeats one of them. */
    private static List<InsuranceBenefitRule> contractRows(List<InsuranceBenefitRule> rules) {
        return rules == null ? List.of() : rules.stream().filter(rule -> rule != null && !rule.globalDefault()).toList();
    }

    private static Optional<InsuranceBenefitRule> inNetwork(
            List<InsuranceBenefitRule> rows,
            EligibilityBenefitScope scope,
            EligibilityNetworkType network
    ) {
        Optional<InsuranceBenefitRule> exact = rows.stream()
                .filter(row -> scopeOf(row) == scope && networkOf(row) == network)
                .findFirst();
        return exact.isPresent()
                ? exact
                : rows.stream().filter(row -> scopeOf(row) == scope && networkOf(row) == null).findFirst();
    }

    private static boolean hasCopayment(InsuranceBenefitRule rule) {
        return rule.patientCopaymentPercentage() != null || rule.patientMaximumCopayment() != null;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
