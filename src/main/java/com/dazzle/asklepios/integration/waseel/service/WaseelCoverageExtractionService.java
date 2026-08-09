package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.dto.WaseelBenefitDetail;
import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCostBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WaseelCoverageExtractionService {

    private final ObjectMapper objectMapper;

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

    public List<InsuranceBenefitRule> extractBenefitRules(String responseJson) {
        try {
            EligibilityResponse response =
                    objectMapper.readValue(
                            responseJson,
                            EligibilityResponse.class
                    );

            if (response.coverages() == null || response.coverages().isEmpty()) {
                return List.of();
            }

            return extractBenefitRules(response.coverages().get(0));
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

                    String ruleKey = categoryKey + "::" + firstNonBlankValue(itemName, itemCode, "default");
                    CategoryRuleBuilder builder =
                            builders.computeIfAbsent(
                                    ruleKey,
                                    ignored -> new CategoryRuleBuilder(categoryKey, itemName, itemCode)
                            );

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

        InsuranceBenefitRule globalRule = null;
        for (CategoryRuleBuilder builder : builders.values()) {
            InsuranceBenefitRule rule = builder.build(null);
            if (rule.patientCopaymentPercentage() != null
                    || rule.patientMaximumCopayment() != null
                    || rule.maximumBenefit() != null) {
                rules.add(rule);
                if (globalRule == null && hasCopayment(rule)) {
                    globalRule = rule;
                }
            }
        }

        if (globalRule != null) {
            rules.add(
                    new InsuranceBenefitRule(
                            null,
                            InsuranceBenefitRule.GLOBAL_CATEGORY,
                            globalRule.itemName(),
                            globalRule.itemCode(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            globalRule.maximumBenefit(),
                            globalRule.approvalLimit(),
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

        if (isCopaymentPercent(typeDisplay)) {
            builder.patientCopaymentPercentage = new BigDecimal(value);
            builder.unit = unit;
            return;
        }

        if (isCopaymentCap(typeDisplay)) {
            builder.patientMaximumCopayment = new BigDecimal(value);
            builder.currency = unit != null ? unit : "SAR";
            return;
        }

        if (isMaximumBenefit(typeDisplay, typeCode)) {
            builder.maximumBenefit = new BigDecimal(value);
            builder.currency = unit != null ? unit : "SAR";
            return;
        }

        if (isApprovalLimit(typeDisplay, typeCode)) {
            builder.approvalLimit = new BigDecimal(value);
            builder.currency = unit != null ? unit : "SAR";
        }
    }

    private boolean hasCopayment(InsuranceBenefitRule rule) {
        return rule.patientCopaymentPercentage() != null
                || rule.patientMaximumCopayment() != null;
    }

    private boolean isMaximumBenefit(String typeDisplay, String typeCode) {
        String normalizedDisplay = normalize(typeDisplay);
        String normalizedCode = normalize(typeCode);
        return normalizedDisplay.contains("benefit")
                || normalizedDisplay.contains("maximum")
                || "benefit".equals(normalizedCode);
    }

    private boolean isApprovalLimit(String typeDisplay, String typeCode) {
        String normalizedDisplay = normalize(typeDisplay);
        String normalizedCode = normalize(typeCode);
        return normalizedDisplay.contains("approval")
                || normalizedCode.contains("approval");
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
        }

        private InsuranceBenefitRule build(String exceptionsJson) {
            return new InsuranceBenefitRule(
                    null,
                    benefitCategory,
                    itemName,
                    itemCode,
                    null,
                    null,
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
            String policyNumber = null;
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
                    response.coverages().get(0);

            memberId =
                    coverage.memberId();

            policyNumber =
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

            if (
                    copayValues.containsKey(
                            "percent"
                    )
            ) {
                copaymentPercent =
                        copayValues.get(
                                "percent"
                        );
            }

            if (
                    copayValues.containsKey(
                            "cap"
                    )
            ) {
                copaymentCap =
                        copayValues.get(
                                "cap"
                        );
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
                    policyNumber,
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
                    extractBenefitRules(coverage)
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
                List.of()
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

                    if (
                            isCopaymentPercent(
                                    typeDisplay
                            )
                    ) {
                        copayValues.put(
                                "percent",
                                new BigDecimal(
                                        value
                                )
                        );
                    }

                    if (
                            isCopaymentCap(
                                    typeDisplay
                            )
                    ) {
                        copayValues.put(
                                "cap",
                                new BigDecimal(
                                        value
                                )
                        );
                    }
                }
            }
        }

        return copayValues;
    }

    private boolean isCopaymentPercent(
            String typeDisplay
    ) {
        if (
                typeDisplay == null
                        || typeDisplay.isBlank()
        ) {
            return false;
        }

        String normalized =
                typeDisplay
                        .trim()
                        .toLowerCase();

        return normalized.equals(
                "copayment percent per service."
        )
                || normalized.equals(
                "copayment percent per service"
        );
    }

    private boolean isCopaymentCap(
            String typeDisplay
    ) {
        if (
                typeDisplay == null
                        || typeDisplay.isBlank()
        ) {
            return false;
        }

        String normalized =
                typeDisplay
                        .trim()
                        .toLowerCase();

        return normalized.equals(
                "copayment maximum per service."
        )
                || normalized.equals(
                "copayment maximum per service"
        );
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
}
