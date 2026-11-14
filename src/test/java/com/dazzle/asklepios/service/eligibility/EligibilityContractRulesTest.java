package com.dazzle.asklepios.service.eligibility;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityBenefitScope;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityLimitType;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityNetworkType;
import com.dazzle.asklepios.domain.enumeration.eligibility.EligibilityPeriodBasis;
import com.dazzle.asklepios.integration.waseel.service.EligibilityCoverageMatcher;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.service.InsuranceBenefitRuleMatcher;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.service.eligibility.EligibilityContractRules.ContractLimit;
import com.dazzle.asklepios.service.eligibility.EligibilityContractRules.LimitBreach;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The eligibility contract applied to visit lines, from the eligibility response through the same
 * extraction that stores patient_insurance_benefit_rules. ANNUAL_POLICY has Sara's shape (limits on
 * their own rows, annual term), LIFETIME_POLICY Taha's (limits and copayment on one row, policy copay
 * in cost beneficiaries).
 */
class EligibilityContractRulesTest {

    private static final EligibilityNetworkType IN = EligibilityNetworkType.IN_NETWORK;

    private static final String ANNUAL_POLICY = """
            { "coverages": [{ "memberId": "M-1", "policyNumber": "P-1", "inforce": "true",
              "items": {
                "Dental Coverage.": [{ "name": "Dental Coverage", "description": "Cost of Dental Treatment",
                  "network": "In Network", "term": "Annual, renewing on the anniversary",
                  "benefits": [
                    {"type": "Benefit", "typeDisplay": "Maximum benefit allowable.", "value": "2000"},
                    {"type": "approval-limit", "value": "500"} ] }],
                "Health Benefit Plan Coverage.": [
                  { "name": "Health Benefit Plan Coverage", "description": "Policy Maximum Annual Limit and Approval limit",
                    "network": "In Network", "term": "Annual, renewing on the anniversary",
                    "benefits": [
                      {"type": "Benefit", "typeDisplay": "Maximum benefit allowable.", "value": "500000"},
                      {"type": "approval-limit", "value": "5000"} ] },
                  { "name": "Health Benefit Plan Coverage", "description": "Outpatient Treatment expenses-within minimum unified network",
                    "network": "In Network", "term": "Per day",
                    "benefits": [
                      {"type": "Copayment Percent per service", "typeDisplay": "Copayment Percent per service.", "value": "20"},
                      {"type": "Copayment maximum per service", "typeDisplay": "Copayment maximum per service.", "value": "75"} ] },
                  { "name": "Health Benefit Plan Coverage", "description": "Outpatient Treatment expenses-for hospital out of the minimum unified network",
                    "network": "Out of Network", "term": "Per day",
                    "benefits": [
                      {"type": "Copayment Percent per service", "typeDisplay": "Copayment Percent per service.", "value": "20"},
                      {"type": "Copayment maximum per service", "typeDisplay": "Copayment maximum per service.", "value": "300"} ] } ],
                "OP Prescribed Invented Medicines - with generic alternative.": [{
                  "name": "OP Prescribed Invented Medicines - with generic alternative", "network": "In Network",
                  "term": "Annual, renewing on the anniversary",
                  "benefits": [{"type": "Benefit", "typeDisplay": "Maximum benefit allowable.", "value": "300"}] }],
                "Consultation.": [{ "name": "Consultation", "description": "Consultation-Specialist", "network": "In Network",
                  "term": "Per day",
                  "benefits": [{"type": "Benefit", "typeDisplay": "Maximum benefit allowable.", "value": "200"}] }]
              } }] }
            """;

    private static final String LIFETIME_POLICY = """
            { "coverages": [{ "memberId": "M-2", "policyNumber": "P-2", "inforce": "true",
              "costBeneficiaries": [
                {"costBeneficiaryType": "copaypct", "costBeneficiaryQut": "20"},
                {"costBeneficiaryType": "maxcopay", "costBeneficiaryMoney": 75} ],
              "items": {
                "Health Benefit Plan Coverage.": [{ "name": "Health Benefit Plan Coverage", "network": "In Network",
                  "term": "For the total term, lifetime, of the policy or coverage",
                  "benefits": [
                    {"type": "approval-limit", "value": "1500"},
                    {"type": "Copayment maximum per service", "typeDisplay": "Copayment maximum per service.", "value": "75"},
                    {"type": "Copayment Percent per service", "typeDisplay": "Copayment Percent per service.", "value": "20"} ] }],
                "Dental Coverage.": [{ "name": "Dental Coverage", "network": "In Network",
                  "term": "For the total term, lifetime, of the policy or coverage",
                  "benefits": [
                    {"type": "approval-limit", "value": "1"},
                    {"type": "Copayment maximum per service", "typeDisplay": "Copayment maximum per service.", "value": "50"},
                    {"type": "Copayment Percent per service", "typeDisplay": "Copayment Percent per service.", "value": "10"} ] }]
              } }] }
            """;

    // ---- what the old table stores ----

    @Test
    void theStoredRulesKeepTheTermAndTheNetwork() {
        InsuranceBenefitRule dental = rules(ANNUAL_POLICY).stream()
                .filter(rule -> "Cost of Dental Treatment".equals(rule.itemName()))
                .findFirst()
                .orElseThrow();

        assertThat(dental.term()).isEqualTo("Annual, renewing on the anniversary");
        assertThat(dental.networkType()).isEqualTo("IN_NETWORK");
        assertThat(dental.approvalLimit()).isEqualByComparingTo("500");
        assertThat(dental.maximumBenefit()).isEqualByComparingTo("2000");
    }

    @Test
    void benefitRulesAreScopedFromThePayerWording() {
        List<InsuranceBenefitRule> rules = rules(ANNUAL_POLICY);

        assertThat(scopeOf(rules, "Cost of Dental Treatment")).isEqualTo(EligibilityBenefitScope.DENTAL);
        assertThat(scopeOf(rules, "Policy Maximum Annual Limit and Approval limit")).isEqualTo(EligibilityBenefitScope.POLICY);
        assertThat(scopeOf(rules, "Outpatient Treatment expenses-within minimum unified network")).isEqualTo(EligibilityBenefitScope.OUTPATIENT);
        assertThat(scopeOf(rules, "OP Prescribed Invented Medicines - with generic alternative")).isEqualTo(EligibilityBenefitScope.MEDICATION);
        assertThat(scopeOf(rules, "Consultation-Specialist")).isEqualTo(EligibilityBenefitScope.OTHER);
        assertThat(scopeOf(rules(LIFETIME_POLICY), "Policy copay")).isEqualTo(EligibilityBenefitScope.POLICY);
    }

    // ---- scope of a line ----

    @Test
    void dentalTreatmentIsDentalButClinicDefaultServicesAreNot() {
        assertThat(EligibilityContractRules.scopeOf(line(ServiceSource.DENTAL_PROCEDURE, BillingItemTypes.PROCEDURE)))
                .isEqualTo(EligibilityBenefitScope.DENTAL);
        assertThat(EligibilityContractRules.scopeOf(line(ServiceSource.DENTAL_CHART_CDT_TREATMENT, BillingItemTypes.SERVICE)))
                .isEqualTo(EligibilityBenefitScope.DENTAL);
        assertThat(EligibilityContractRules.scopeOf(line(ServiceSource.ENCOUNTER_DEFAULT_SERVICE, BillingItemTypes.SERVICE)))
                .isEqualTo(EligibilityBenefitScope.OUTPATIENT);
        assertThat(EligibilityContractRules.scopeOf(line(ServiceSource.PRESCRIPTION, BillingItemTypes.MEDICATION)))
                .isEqualTo(EligibilityBenefitScope.MEDICATION);
    }

    // ---- patient share ----

    @Test
    void outpatientShareInAndOutOfNetwork() {
        List<InsuranceBenefitRule> rules = rules(ANNUAL_POLICY);

        InsuranceBenefitRule in = EligibilityContractRules.copaymentFor(rules, EligibilityBenefitScope.OUTPATIENT, IN).orElseThrow();
        assertThat(in.patientCopaymentPercentage()).isEqualByComparingTo("20");
        assertThat(in.patientMaximumCopayment()).isEqualByComparingTo("75");

        InsuranceBenefitRule out = EligibilityContractRules.copaymentFor(
                rules, EligibilityBenefitScope.OUTPATIENT, EligibilityNetworkType.OUT_OF_NETWORK).orElseThrow();
        assertThat(out.patientMaximumCopayment()).isEqualByComparingTo("300");
    }

    @Test
    void dentalWithoutItsOwnShareUsesTheOutpatientShare() {
        InsuranceBenefitRule dental = EligibilityContractRules.copaymentFor(rules(ANNUAL_POLICY), EligibilityBenefitScope.DENTAL, IN)
                .orElseThrow();

        assertThat(dental.itemName()).startsWith("Outpatient Treatment");
    }

    @Test
    void dentalWithItsOwnShareUsesIt() {
        InsuranceBenefitRule dental = EligibilityContractRules.copaymentFor(rules(LIFETIME_POLICY), EligibilityBenefitScope.DENTAL, IN)
                .orElseThrow();

        assertThat(dental.patientCopaymentPercentage()).isEqualByComparingTo("10");
        assertThat(dental.patientMaximumCopayment()).isEqualByComparingTo("50");
    }

    @Test
    void generalServiceUsesThePolicyShareWhenThereIsNoOutpatientRow() {
        InsuranceBenefitRule general = EligibilityContractRules.copaymentFor(rules(LIFETIME_POLICY), EligibilityBenefitScope.OUTPATIENT, IN)
                .orElseThrow();

        assertThat(EligibilityContractRules.scopeOf(general)).isEqualTo(EligibilityBenefitScope.POLICY);
        assertThat(general.patientCopaymentPercentage()).isEqualByComparingTo("20");
        assertThat(general.patientMaximumCopayment()).isEqualByComparingTo("75");
    }

    // ---- limits ----

    @Test
    void dentalTreatmentAboveTheDentalApprovalLimitNeedsPreAuthorization() {
        Optional<LimitBreach> breach = breach(ANNUAL_POLICY, EligibilityBenefitScope.DENTAL, "600", Map.of());

        assertThat(breach).isPresent();
        assertThat(breach.get().limit().limitType()).isEqualTo(EligibilityLimitType.APPROVAL_LIMIT);
        assertThat(breach.get().limit().limitValue()).isEqualByComparingTo("500");
        assertThat(breach.get().limit().periodBasis()).isEqualTo(EligibilityPeriodBasis.POLICY_YEAR);
    }

    @Test
    void dentalTreatmentUnderTheLimitsIsBilled() {
        assertThat(breach(ANNUAL_POLICY, EligibilityBenefitScope.DENTAL, "224", Map.of())).isEmpty();
    }

    @Test
    void dentalTreatmentAboveTheYearlyDentalMaximumNeedsPreAuthorization() {
        Optional<LimitBreach> breach = breach(ANNUAL_POLICY, EligibilityBenefitScope.DENTAL, "300",
                Map.of(EligibilityBenefitScope.DENTAL, new BigDecimal("1800")));

        assertThat(breach).isPresent();
        assertThat(breach.get().limit().limitType()).isEqualTo(EligibilityLimitType.MAXIMUM_BENEFIT);
        assertThat(breach.get().amountUsed()).isEqualByComparingTo("1800");
    }

    @Test
    void clinicDefaultServicesUseThePolicyLimitNotTheDentalOne() {
        List<ContractLimit> limits = EligibilityContractRules.limitsFor(rules(ANNUAL_POLICY), EligibilityBenefitScope.OUTPATIENT, IN);

        assertThat(limits).allMatch(limit -> limit.scope() == EligibilityBenefitScope.POLICY);
        assertThat(breach(ANNUAL_POLICY, EligibilityBenefitScope.OUTPATIENT, "224", Map.of())).isEmpty();
        assertThat(breach(ANNUAL_POLICY, EligibilityBenefitScope.OUTPATIENT, "6000", Map.of())).isPresent();
    }

    @Test
    void defaultServicesDoNotUseTheDentalLimit() {
        ContractLimit dentalMaximum = EligibilityContractRules.limitsFor(rules(ANNUAL_POLICY), EligibilityBenefitScope.DENTAL, IN).stream()
                .filter(limit -> limit.scope() == EligibilityBenefitScope.DENTAL
                        && limit.limitType() == EligibilityLimitType.MAXIMUM_BENEFIT)
                .findFirst()
                .orElseThrow();

        assertThat(EligibilityContractRules.countsToward(dentalMaximum, line(ServiceSource.DENTAL_PROCEDURE, BillingItemTypes.PROCEDURE)))
                .isTrue();
        assertThat(EligibilityContractRules.countsToward(dentalMaximum, line(ServiceSource.ENCOUNTER_DEFAULT_SERVICE, BillingItemTypes.SERVICE)))
                .isFalse();
    }

    @Test
    void medicinesUseTheirOwnYearlyMaximum() {
        assertThat(breach(ANNUAL_POLICY, EligibilityBenefitScope.MEDICATION, "100",
                Map.of(EligibilityBenefitScope.MEDICATION, new BigDecimal("250")))).isPresent();
        assertThat(breach(ANNUAL_POLICY, EligibilityBenefitScope.MEDICATION, "40",
                Map.of(EligibilityBenefitScope.MEDICATION, new BigDecimal("250")))).isEmpty();
    }

    @Test
    void lifetimePolicyApprovalLimitsApply() {
        assertThat(breach(LIFETIME_POLICY, EligibilityBenefitScope.DENTAL, "50", Map.of())).isPresent();
        assertThat(breach(LIFETIME_POLICY, EligibilityBenefitScope.OUTPATIENT, "2000", Map.of())).isPresent();
        assertThat(breach(LIFETIME_POLICY, EligibilityBenefitScope.OUTPATIENT, "1000", Map.of())).isEmpty();
        assertThat(EligibilityContractRules.limitsFor(rules(LIFETIME_POLICY), EligibilityBenefitScope.OUTPATIENT, IN))
                .allMatch(limit -> limit.periodBasis() == EligibilityPeriodBasis.POLICY_TERM);
    }

    // ---- period and in force ----

    @Test
    void policyYearRenewsOnTheAnniversary() {
        LocalDate start = LocalDate.of(2021, 9, 28);

        assertThat(EligibilityContractRules.periodStart(EligibilityPeriodBasis.POLICY_YEAR, start, LocalDate.of(2026, 11, 12)))
                .isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(EligibilityContractRules.periodStart(EligibilityPeriodBasis.POLICY_YEAR, start, LocalDate.of(2026, 9, 1)))
                .isEqualTo(LocalDate.of(2025, 9, 28));
        assertThat(EligibilityContractRules.periodStart(EligibilityPeriodBasis.POLICY_TERM, start, LocalDate.of(2026, 9, 1)))
                .isEqualTo(start);
        assertThat(EligibilityContractRules.periodStart(EligibilityPeriodBasis.PER_DAY, start, LocalDate.of(2026, 9, 1)))
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(EligibilityContractRules.periodStart(EligibilityPeriodBasis.PER_ENCOUNTER, start, LocalDate.of(2026, 9, 1)))
                .isNull();
    }

    @Test
    void theContractIsInForceUntilTheBenefitPeriodEnds() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        PatientInsurance insurance = new PatientInsurance();
        insurance.setInforce("true");
        insurance.setBenefitEndDate(LocalDate.of(2028, 9, 28));

        assertThat(EligibilityContractReader.isInForce(insurance, today)).isTrue();

        insurance.setBenefitEndDate(LocalDate.of(2026, 11, 13));
        assertThat(EligibilityContractReader.isInForce(insurance, today)).isFalse();

        insurance.setBenefitEndDate(null);
        insurance.setInforce("false");
        assertThat(EligibilityContractReader.isInForce(insurance, today)).isFalse();
    }

    @Test
    void visitNetworkFollowsTheSiteEligibility() {
        assertThat(EligibilityContractRules.visitNetwork("eligible")).isEqualTo(IN);
        assertThat(EligibilityContractRules.visitNetwork("out-network")).isEqualTo(EligibilityNetworkType.OUT_OF_NETWORK);
        assertThat(EligibilityContractRules.visitNetwork(null)).isEqualTo(IN);
    }

    // ---- helpers ----

    private static Optional<LimitBreach> breach(
            String json,
            EligibilityBenefitScope scope,
            String amount,
            Map<EligibilityBenefitScope, BigDecimal> usedByScope
    ) {
        List<ContractLimit> limits = EligibilityContractRules.limitsFor(rules(json), scope, IN);
        return EligibilityContractRules.firstBreach(
                limits,
                new BigDecimal(amount),
                limit -> usedByScope.getOrDefault(limit.scope(), BigDecimal.ZERO)
        );
    }

    /** The rules the eligibility check stores in patient_insurance_benefit_rules for this response. */
    private static List<InsuranceBenefitRule> rules(String json) {
        return new WaseelCoverageExtractionService(new ObjectMapper(), new InsuranceBenefitRuleMatcher(), new EligibilityCoverageMatcher())
                .extractBenefitRules(json);
    }

    private static EligibilityBenefitScope scopeOf(List<InsuranceBenefitRule> rules, String itemName) {
        return rules.stream()
                .filter(rule -> !rule.globalDefault() && itemName.equals(rule.itemName()))
                .map(EligibilityContractRules::scopeOf)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rule " + itemName));
    }

    private static PatientServiceAndProduct line(ServiceSource source, BillingItemTypes type) {
        return PatientServiceAndProduct.builder().serviceSource(source).billingItemType(type).build();
    }
}
