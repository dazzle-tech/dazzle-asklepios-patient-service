package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.dto.WaseelBenefitDetail;
import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCostBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
                    )
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
                benefits
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
            benefits.add(
                    new WaseelBenefitDetail(
                            "Cost Beneficiary",
                            null,
                            null,
                            costBeneficiary.costBeneficiaryType(),
                            null,
                            costBeneficiary.costBeneficiaryQut(),
                            costBeneficiary.costBeneficiaryMoney() == null
                                    ? null
                                    : costBeneficiary
                                            .costBeneficiaryMoney()
                                            .toString()
                    )
            );
        }
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
