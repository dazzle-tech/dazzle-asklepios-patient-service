package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.dto.WaseelBenefitDetail;
import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCostBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.dazzle.asklepios.service.InsuranceBenefitRuleMatcher;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WaseelCoverageExtractionService {

    private final ObjectMapper objectMapper;
    private final InsuranceBenefitRuleMatcher benefitRuleMatcher;
    private final EligibilityCoverageMatcher coverageMatcher;

    public InsuranceCoverage extractCoverage(
            String responseJson
    ) {
        WaseelCoverageDetails details =
                extractCoverageDetails(
                        responseJson
                );

        return new InsuranceCoverage(
                details.copaymentPercent(),
                details.copaymentCap()
        );
    }

    /**
     * Coverage is billable as insurance only when Waseel returned coverage
     * details that are actually in-force. A successful eligibility transaction
     * is not enough.
     */
    public boolean isCoverageInForce(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return false;
        }

        try {
            EligibilityResponse response =
                    objectMapper.readValue(
                            responseJson,
                            EligibilityResponse.class
                    );

            return isCoverageInForce(response);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean isCoverageInForce(EligibilityResponse response) {
        if (response == null) {
            return false;
        }

        if (isNotInForceDisposition(response.disposition())) {
            return false;
        }

        if (response.coverages() == null || response.coverages().isEmpty()) {
            return false;
        }

        EligibilityCoverageDTO coverage = response.coverages().get(0);
        if (coverage == null) {
            return false;
        }

        if (isNotInForceDisposition(coverage.notInforceReason())) {
            return false;
        }

        return !isExplicitlyNotInForce(coverage.inforce());
    }

    private boolean isNotInForceDisposition(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim().toLowerCase();
        return normalized.contains("not in-force")
                || normalized.contains("not in force")
                || normalized.contains("not inforce");
    }

    private boolean isExplicitlyNotInForce(String inforce) {
        if (inforce == null || inforce.isBlank()) {
            return false;
        }

        String normalized = inforce.trim().toLowerCase();
        return normalized.equals("false")
                || normalized.equals("no")
                || normalized.equals("0")
                || normalized.equals("n");
    }

    public List<InsuranceBenefitRule> extractBenefitRules(String responseJson) {
        return extractBenefitRules(responseJson, null, null);
    }

    public List<InsuranceBenefitRule> extractBenefitRules(
            String responseJson,
            String memberCardId,
            String policyNumber
    ) {
        try {
            EligibilityResponse response =
                    objectMapper.readValue(
                            responseJson,
                            EligibilityResponse.class
                    );

            if (response.coverages() == null || response.coverages().isEmpty()) {
                return List.of();
            }

            EligibilityCoverageDTO coverage =
                    coverageMatcher.findMatchingCoverage(
                            response.coverages(),
                            memberCardId,
                            policyNumber
                    );

            if (coverage == null && memberCardId == null && policyNumber == null) {
                coverage = response.coverages().get(0);
            }

            if (coverage == null) {
                return List.of();
            }

            return extractBenefitRules(coverage);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to extract benefit rules from Waseel response",
                    exception
            );
        }
    }

    public List<InsuranceBenefitRule> extractBenefitRules(
            EligibilityCoverageDTO coverage
    ) {
        if (coverage == null) {
            return List.of();
        }

        List<InsuranceBenefitRule> rules = new ArrayList<>();
        Map<String, CategoryRuleBuilder> builders = new LinkedHashMap<>();

        appendCostBeneficiaryRules(builders, coverage.costBeneficiaries());

        if (coverage.items() instanceof Map<?, ?> rawItems) {
            for (Map.Entry<?, ?> categoryEntry : rawItems.entrySet()) {
                String categoryKey =
                        categoryEntry.getKey() == null
                                ? ""
                                : categoryEntry.getKey().toString().trim();

                if (!(categoryEntry.getValue() instanceof List<?> categoryItems)) {
                    continue;
                }

                for (Object categoryItemObject : categoryItems) {
                    if (!(categoryItemObject instanceof Map<?, ?> itemMap)) {
                        continue;
                    }

                    Map<String, Object> item = castToStringObjectMap(itemMap);
                    String itemName =
                            firstNonBlank(
                                    item,
                                    "description",
                                    "name",
                                    "productOrServiceDisplay",
                                    "display"
                            );
                    String itemCode =
                            firstNonBlank(
                                    item,
                                    "code",
                                    "productOrService",
                                    "serviceCode",
                                    "itemCode"
                            );

                    String ruleKey = categoryKey + "::" + firstNonBlankValue(itemName, itemCode, "default");
                    CategoryRuleBuilder builder =
                            builders.computeIfAbsent(
                                    ruleKey,
                                    ignored -> new CategoryRuleBuilder(categoryKey, itemName, itemCode)
                            );
                    builder.applyItemContext(item);

                    Object benefitsObject = item.get("benefits");
                    if (!(benefitsObject instanceof List<?> benefitList)) {
                        continue;
                    }

                    for (Object benefitObject : benefitList) {
                        if (!(benefitObject instanceof Map<?, ?> benefitMap)) {
                            continue;
                        }

                        applyBenefitEntry(
                                builder,
                                castToStringObjectMap(benefitMap)
                        );
                    }
                }
            }
        }

        for (CategoryRuleBuilder builder : builders.values()) {
            InsuranceBenefitRule rule = builder.build(null);
            if (hasCopayment(rule)
                    || rule.maximumBenefit() != null
                    || rule.approvalLimit() != null) {
                rules.add(rule);
            }
        }

        InsuranceBenefitRule globalRule =
                benefitRuleMatcher.selectPreferredDefaultRule(rules, true);

        if (globalRule != null) {
            rules.add(
                    new InsuranceBenefitRule(
                            null,
                            InsuranceBenefitRule.GLOBAL_CATEGORY,
                            globalRule.itemName(),
                            globalRule.itemCode(),
                            globalRule.networkType(),
                            globalRule.providerType(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            globalRule.patientCopaymentPercentage(),
                            globalRule.patientMaximumCopayment(),
                            true,
                            null
                    )
            );
        }

        return List.copyOf(rules);
    }

    private void appendCostBeneficiaryRules(
            Map<String, CategoryRuleBuilder> builders,
            List<EligibilityCostBeneficiaryDTO> costBeneficiaries
    ) {
        if (costBeneficiaries == null || costBeneficiaries.isEmpty()) {
            return;
        }

        for (EligibilityCostBeneficiaryDTO costBeneficiary : costBeneficiaries) {
            String type = clean(costBeneficiary.costBeneficiaryType());
            if (type == null) {
                continue;
            }

            String ruleKey = "cost-beneficiary::" + type.toLowerCase(Locale.ROOT);
            CategoryRuleBuilder builder =
                    builders.computeIfAbsent(
                            ruleKey,
                            ignored ->
                                    new CategoryRuleBuilder(
                                            "Cost Beneficiary",
                                            formatCostBeneficiaryLabel(type),
                                            type
                                    )
                    );

            if (costBeneficiary.costBeneficiaryMoney() != null) {
                builder.patientMaximumCopayment =
                        BigDecimal.valueOf(costBeneficiary.costBeneficiaryMoney());
            }
        }
    }

    private void applyBenefitEntry(
            CategoryRuleBuilder builder,
            Map<String, Object> benefit
    ) {
        String typeDisplay = stringValue(benefit.get("typeDisplay"));
        String typeCode =
                firstNonBlank(
                        benefit,
                        "type",
                        "typeCode",
                        "code"
                );
        String value = stringValue(benefit.get("value"));
        String unit = stringValue(benefit.get("unit"));

        if (value == null || value.isBlank()) {
            return;
        }

        WaseelBenefitEntryClassifier.EntryType entryType =
                WaseelBenefitEntryClassifier.classify(typeDisplay, typeCode);

        switch (entryType) {
            case PATIENT_COPAYMENT_PERCENT -> {
                builder.patientCopaymentPercentage = new BigDecimal(value);
                builder.unit = unit;
            }
            case PATIENT_COPAYMENT_MAXIMUM -> {
                builder.patientMaximumCopayment = new BigDecimal(value);
                builder.currency = unit != null ? unit : "SAR";
            }
            case INSURANCE_MAXIMUM_BENEFIT -> {
                builder.maximumBenefit = new BigDecimal(value);
                builder.currency = unit != null ? unit : "SAR";
            }
            case INSURANCE_APPROVAL_LIMIT -> {
                builder.approvalLimit = new BigDecimal(value);
                builder.currency = unit != null ? unit : "SAR";
            }
            default -> {
                // ignore unclassified benefit entries
            }
        }
    }

    private boolean hasCopayment(InsuranceBenefitRule rule) {
        return rule.patientCopaymentPercentage() != null
                || rule.patientMaximumCopayment() != null;
    }

    private String firstNonBlankValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "default";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class CategoryRuleBuilder {

        private final String benefitCategory;
        private final String itemName;
        private final String itemCode;
        private String networkType;
        private String providerType;
        private BigDecimal patientCopaymentPercentage;
        private BigDecimal patientMaximumCopayment;
        private BigDecimal maximumBenefit;
        private BigDecimal approvalLimit;
        private String unit;
        private String currency;

        private CategoryRuleBuilder(
                String benefitCategory,
                String itemName,
                String itemCode
        ) {
            this.benefitCategory = benefitCategory;
            this.itemName = itemName;
            this.itemCode = itemCode;
            parseContextFromItemName(itemName);
        }

        private void applyItemContext(Map<String, Object> item) {
            if (item == null) {
                return;
            }

            Object networkObject = item.get("network");
            String network = networkObject == null ? null : networkObject.toString();
            applyNetwork(network);

            String description = item.get("description") == null
                    ? null
                    : item.get("description").toString();
            if (description != null && !description.isBlank()) {
                parseContextFromItemName(description);
            }
        }

        private void applyNetwork(String network) {
            if (network == null || network.isBlank()) {
                return;
            }

            String normalized = network.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("out of") || normalized.contains("out-of")) {
                networkType = "OUT_OF_NETWORK";
            } else if (normalized.contains("in network") || normalized.equals("in")
                    || normalized.startsWith("in ")) {
                networkType = "IN_NETWORK";
            }
        }

        private void parseContextFromItemName(String name) {
            String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);

            if (normalized.contains("out of network")
                    || normalized.contains("out-of-network")) {
                networkType = "OUT_OF_NETWORK";
            } else if (normalized.contains("in network")
                    || normalized.contains("in-network")) {
                networkType = "IN_NETWORK";
            }

            if (normalized.contains("outpatient")) {
                providerType = "OUTPATIENT";
            } else if (normalized.contains("inpatient")) {
                providerType = "INPATIENT";
            } else if (normalized.contains("other healthcare")
                    || normalized.contains("other health care")) {
                providerType = "OTHER_HEALTHCARE_PROVIDER";
            }
        }

        private InsuranceBenefitRule build(String exceptionsJson) {
            return new InsuranceBenefitRule(
                    null,
                    benefitCategory,
                    itemName,
                    itemCode,
                    networkType,
                    providerType,
                    null,
                    unit,
                    currency,
                    maximumBenefit,
                    approvalLimit,
                    patientCopaymentPercentage,
                    patientMaximumCopayment,
                    false,
                    exceptionsJson
            );
        }
    }

    public WaseelCoverageDetails extractCoverageDetails(
            String responseJson
    ) {
        return extractCoverageDetails(responseJson, null, null);
    }

    public WaseelCoverageDetails extractCoverageDetails(
            String responseJson,
            String memberCardId,
            String policyNumber
    ) {
        try {
            EligibilityResponse response =
                    objectMapper.readValue(
                            responseJson,
                            EligibilityResponse.class
                    );

            BigDecimal copaymentPercent =
                    BigDecimal.ZERO;

            BigDecimal copaymentCap =
                    BigDecimal.ZERO;

            List<WaseelBenefitDetail> benefits =
                    new ArrayList<>();

            String memberId = null;
            String resolvedPolicyNumber = null;
            String policyHolder = null;
            String network = null;
            String inforce = null;
            String coverageStatus = null;

            if (
                    response.coverages() == null
                            || response.coverages().isEmpty()
            ) {
                return emptyDetails(
                        copaymentPercent,
                        copaymentCap,
                        benefits
                );
            }

            EligibilityCoverageDTO coverage =
                    coverageMatcher.findMatchingCoverage(
                            response.coverages(),
                            memberCardId,
                            policyNumber
                    );

            if (coverage == null && memberCardId == null && policyNumber == null) {
                coverage = response.coverages().get(0);
            }

            if (coverage == null) {
                return emptyDetails(
                        copaymentPercent,
                        copaymentCap,
                        benefits
                );
            }

            memberId =
                    coverage.memberId();

            resolvedPolicyNumber =
                    coverage.policyNumber();

            policyHolder =
                    coverage.policyHolder();

            network =
                    coverage.network();

            inforce =
                    coverage.inforce();

            coverageStatus =
                    coverage.status();

            appendCostBeneficiaries(
                    benefits,
                    coverage.costBeneficiaries()
            );

            appendClassList(
                    benefits,
                    coverage.classList()
            );

            Map<String, BigDecimal> copayValues =
                    appendCoverageItems(
                            benefits,
                            coverage.items()
                    );

            List<InsuranceBenefitRule> benefitRules =
                    extractBenefitRules(coverage);
            InsuranceBenefitRule preferredRule =
                    benefitRuleMatcher.selectPreferredDefaultRule(
                            benefitRules,
                            true
                    );

            if (preferredRule != null) {
                if (preferredRule.patientCopaymentPercentage() != null) {
                    copaymentPercent = preferredRule.patientCopaymentPercentage();
                }
                if (preferredRule.patientMaximumCopayment() != null) {
                    copaymentCap = preferredRule.patientMaximumCopayment();
                }
            } else {
                if (copayValues.containsKey("percent")) {
                    copaymentPercent = copayValues.get("percent");
                }

                if (copayValues.containsKey("cap")) {
                    copaymentCap = copayValues.get("cap");
                }
            }

            return new WaseelCoverageDetails(
                    null,
                    response.responseId() == null
                            ? null
                            : String.valueOf(
                                    response.responseId()
                            ),
                    null,
                    memberId,
                    resolvedPolicyNumber,
                    policyHolder,
                    network,
                    inforce,
                    coverageStatus,
                    copaymentPercent,
                    copaymentCap,
                    null,
                    List.copyOf(
                            benefits
                    ),
                    extractBenefitRules(coverage),
                    resolveClassName(coverage, "plan"),
                    parseCoverageDate(coverage.benefitEndDate()),
                    null,
                    coverage.type(),
                    coverage.relationship(),
                    resolveClassValue(coverage, "plan"),
                    resolveClassName(coverage, "group"),
                    resolveClassValue(coverage, "group")
            );

        } catch (
                RuntimeException exception
        ) {
            throw exception;

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Failed to extract coverage from Waseel response",
                    exception
            );
        }
    }

    private WaseelCoverageDetails emptyDetails(
            BigDecimal copaymentPercent,
            BigDecimal copaymentCap,
            List<WaseelBenefitDetail> benefits
    ) {
        return new WaseelCoverageDetails(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                copaymentPercent,
                copaymentCap,
                null,
                benefits,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void appendCostBeneficiaries(
            List<WaseelBenefitDetail> benefits,
            List<EligibilityCostBeneficiaryDTO> costBeneficiaries
    ) {
        if (
                costBeneficiaries == null
                        || costBeneficiaries.isEmpty()
        ) {
            return;
        }

        for (
                EligibilityCostBeneficiaryDTO costBeneficiary
                        : costBeneficiaries
        ) {
            String beneficiaryType =
                    clean(costBeneficiary.costBeneficiaryType());

            String value =
                    costBeneficiary.costBeneficiaryMoney() == null
                            ? costBeneficiary.costBeneficiaryQut()
                            : costBeneficiary.costBeneficiaryMoney().toString();

            String unit =
                    costBeneficiary.costBeneficiaryMoney() != null
                            ? "SAR"
                            : null;

            benefits.add(
                    new WaseelBenefitDetail(
                            "Cost Beneficiary",
                            formatCostBeneficiaryLabel(beneficiaryType),
                            null,
                            beneficiaryType,
                            null,
                            value,
                            unit
                    )
            );
        }
    }

    private String formatCostBeneficiaryLabel(String beneficiaryType) {
        if (beneficiaryType == null || beneficiaryType.isBlank()) {
            return "Cost Beneficiary";
        }

        return switch (beneficiaryType.trim().toLowerCase()) {
            case "gpvisit" -> "GP Visit Copay";
            case "spvisit" -> "Specialist Visit";
            default -> beneficiaryType;
        };
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private void appendClassList(
            List<WaseelBenefitDetail> benefits,
            List<EligibilityClassDTO> classList
    ) {
        if (
                classList == null
                        || classList.isEmpty()
        ) {
            return;
        }

        for (
                EligibilityClassDTO classItem
                        : classList
        ) {
            benefits.add(
                    new WaseelBenefitDetail(
                            "Class",
                            classItem.className(),
                            null,
                            classItem.classType(),
                            null,
                            classItem.classValue(),
                            null
                    )
            );
        }
    }

    private Map<String, BigDecimal> appendCoverageItems(
            List<WaseelBenefitDetail> benefits,
            Object itemsObject
    ) {
        Map<String, BigDecimal> copayValues =
                new LinkedHashMap<>();

        if (
                !(itemsObject instanceof Map<?, ?> rawItems)
        ) {
            return copayValues;
        }

        for (
                Map.Entry<?, ?> categoryEntry
                        : rawItems.entrySet()
        ) {
            String categoryKey =
                    categoryEntry.getKey() == null
                            ? ""
                            : categoryEntry
                                    .getKey()
                                    .toString()
                                    .trim();

            if (
                    !(categoryEntry.getValue()
                            instanceof List<?> categoryItems)
            ) {
                continue;
            }

            for (
                    Object categoryItemObject
                            : categoryItems
            ) {
                if (
                        !(categoryItemObject
                                instanceof Map<?, ?> itemMap)
                ) {
                    continue;
                }

                Map<String, Object> item =
                        castToStringObjectMap(
                                itemMap
                        );

                String itemName =
                        firstNonBlank(
                                item,
                                "name",
                                "description",
                                "productOrServiceDisplay",
                                "display"
                        );

                String itemCode =
                        firstNonBlank(
                                item,
                                "code",
                                "productOrService",
                                "serviceCode",
                                "itemCode"
                        );

                Object benefitsObject =
                        item.get(
                                "benefits"
                        );

                if (
                        !(benefitsObject
                                instanceof List<?> benefitList)
                ) {
                    continue;
                }

                for (
                        Object benefitObject
                                : benefitList
                ) {
                    if (
                            !(benefitObject
                                    instanceof Map<?, ?> benefitMap)
                    ) {
                        continue;
                    }

                    Map<String, Object> benefit =
                            castToStringObjectMap(
                                    benefitMap
                            );

                    String typeDisplay =
                            stringValue(
                                    benefit.get(
                                            "typeDisplay"
                                    )
                            );

                    String typeCode =
                            firstNonBlank(
                                    benefit,
                                    "type",
                                    "typeCode",
                                    "code"
                            );

                    String value =
                            stringValue(
                                    benefit.get(
                                            "value"
                                    )
                            );

                    String unit =
                            stringValue(
                                    benefit.get(
                                            "unit"
                                    )
                            );

                    benefits.add(
                            new WaseelBenefitDetail(
                                    categoryKey,
                                    itemName,
                                    itemCode,
                                    typeDisplay,
                                    typeCode,
                                    value,
                                    unit
                            )
                    );

                    if (
                            value == null
                                    || value.isBlank()
                    ) {
                        continue;
                    }

                    WaseelBenefitEntryClassifier.EntryType entryType =
                            WaseelBenefitEntryClassifier.classify(
                                    typeDisplay,
                                    typeCode
                            );

                    if (entryType
                            == WaseelBenefitEntryClassifier.EntryType.PATIENT_COPAYMENT_PERCENT) {
                        copayValues.put(
                                "percent",
                                new BigDecimal(value)
                        );
                    }

                    if (entryType
                            == WaseelBenefitEntryClassifier.EntryType.PATIENT_COPAYMENT_MAXIMUM) {
                        copayValues.put(
                                "cap",
                                new BigDecimal(value)
                        );
                    }
                }
            }
        }

        return copayValues;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(
            Map<?, ?> source
    ) {
        return (Map<String, Object>) source;
    }

    private String firstNonBlank(
            Map<String, Object> source,
            String... keys
    ) {
        for (
                String key
                        : keys
        ) {
            String value =
                    stringValue(
                            source.get(
                                    key
                            )
                    );

            if (
                    value != null
                            && !value.isBlank()
            ) {
                return value;
            }
        }

        return null;
    }

    private String stringValue(
            Object value
    ) {
        return value == null
                ? null
                : value.toString().trim();
    }

    private String resolveClassName(EligibilityCoverageDTO coverage, String classType) {
        if (coverage == null || coverage.classList() == null || classType == null) {
            return null;
        }

        for (EligibilityClassDTO classItem : coverage.classList()) {
            if (classItem != null
                    && classType.equalsIgnoreCase(clean(classItem.classType()))) {
                return clean(classItem.className());
            }
        }

        return null;
    }

    private String resolveClassValue(EligibilityCoverageDTO coverage, String classType) {
        if (coverage == null || coverage.classList() == null || classType == null) {
            return null;
        }

        for (EligibilityClassDTO classItem : coverage.classList()) {
            if (classItem != null
                    && classType.equalsIgnoreCase(clean(classItem.classType()))) {
                return clean(classItem.classValue());
            }
        }

        return null;
    }

    private LocalDate parseCoverageDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            String date = value.trim();
            if (date.length() >= 10) {
                return LocalDate.parse(date.substring(0, 10));
            }
            return LocalDate.parse(date);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
