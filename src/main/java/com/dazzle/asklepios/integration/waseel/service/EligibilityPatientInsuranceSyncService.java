package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCostBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.service.InsuranceBenefitRuleService;
import com.dazzle.asklepios.service.helper.NphiesPayerHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class EligibilityPatientInsuranceSyncService {

    private final PatientInsuranceRepository patientInsuranceRepository;
    private final ObjectMapper objectMapper;
    private final NphiesPayerHelper nphiesPayerHelper;
    private final InsuranceBenefitRuleService insuranceBenefitRuleService;
    private final EligibilityCoverageMatcher coverageMatcher;

    @Transactional
    public PatientInsurance syncFromEligibilityResponse(
            PatientInsurance insurance,
            EligibilityResponse response,
            Long eligibilityRequestId
    ) {
        if (
                insurance == null
                        || insurance.getId() == null
                        || response == null
                        || response.coverages() == null
                        || response.coverages().isEmpty()
        ) {
            return insurance;
        }

        EligibilityCoverageDTO coverage =
                coverageMatcher.findMatchingCoverage(
                        insurance,
                        response.coverages()
                );

        if (coverage == null) {
            return insurance;
        }

        applyCoverage(
                insurance,
                coverage,
                response,
                eligibilityRequestId
        );

        return patientInsuranceRepository.save(insurance);
    }

    private void applyCoverage(
            PatientInsurance insurance,
            EligibilityCoverageDTO coverage,
            EligibilityResponse response,
            Long eligibilityRequestId
    ) {
        setIfBlank(insurance::setMemberCardId, insurance.getMemberCardId(), coverage.memberId());
        setIfBlank(insurance::setNetworkId, insurance.getNetworkId(), coverage.network());
        setIfBlank(insurance::setCoverageType, insurance.getCoverageType(), coverage.type());
        setIfBlank(
                insurance::setRelationWithSubscriber,
                insurance.getRelationWithSubscriber(),
                coverage.relationship()
        );

        LocalDate benefitStartDate = parseDate(coverage.benefitStartDate());
        if (benefitStartDate != null) {
            insurance.setBenefitStartDate(benefitStartDate);
            if (insurance.getIssueDate() == null) {
                insurance.setIssueDate(benefitStartDate);
            }
        }

        LocalDate benefitEndDate = parseDate(coverage.benefitEndDate());
        if (benefitEndDate != null) {
            insurance.setBenefitEndDate(benefitEndDate);
            if (insurance.getExpirationDate() == null) {
                insurance.setExpirationDate(benefitEndDate);
            }
        }

        if (isBlank(insurance.getPayerNphiesId()) && !isBlank(response.payerId())) {
            insurance.setPayerNphiesId(response.payerId().trim());
        }

        applyPayerNameFromNphiesPayers(insurance, response);

        applyClassList(insurance, coverage.classList());
        applyCostBeneficiaries(insurance, coverage.costBeneficiaries());

        insurance.setEligibilityStatus(firstNonBlank(coverage.status(), response.status()));
        insurance.setSiteEligibility(firstNonBlank(coverage.siteEligibility(), response.siteEligibility()));
        insurance.setInforce(clean(coverage.inforce()));

        if (insurance.getMaxLimit() == null) {
            insurance.setMaxLimit(extractAnnualPolicyLimit(coverage));
        }

        insurance.setEligibilityBenefitsJson(buildBenefitsJson(coverage));
        insurance.setLastEligibilityRequestId(eligibilityRequestId);
        insurance.setLastEligibilitySyncedAt(Instant.now());

        insuranceBenefitRuleService.syncFromCoverage(
                insurance,
                coverage,
                eligibilityRequestId
        );
    }

    private void applyPayerNameFromNphiesPayers(
            PatientInsurance insurance,
            EligibilityResponse response
    ) {
        String payerNphiesId = firstNonBlank(insurance.getPayerNphiesId(), response.payerId());
        String payerName = nphiesPayerHelper.resolvePayerDisplayName(
                payerNphiesId,
                insurance.getPayerName()
        );

        if (payerName != null) {
            insurance.setPayerName(payerName);
        }
    }

    private void applyClassList(
            PatientInsurance insurance,
            List<EligibilityClassDTO> classList
    ) {
        if (classList == null || classList.isEmpty()) {
            return;
        }

        for (EligibilityClassDTO classItem : classList) {
            String classType = clean(classItem.classType());
            if (classType == null) {
                continue;
            }

            if ("group".equalsIgnoreCase(classType)) {
                setIfBlank(insurance::setGroupName, insurance.getGroupName(), classItem.className());
                setIfBlank(insurance::setGroupNumber, insurance.getGroupNumber(), classItem.classValue());
            }

            if ("plan".equalsIgnoreCase(classType)) {
                setIfBlank(insurance::setPlanCode, insurance.getPlanCode(), classItem.classValue());
            }
        }
    }

    private void applyCostBeneficiaries(
            PatientInsurance insurance,
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

            if ("gpvisit".equalsIgnoreCase(type) && costBeneficiary.costBeneficiaryMoney() != null) {
                insurance.setGpVisitCopay(
                        BigDecimal.valueOf(costBeneficiary.costBeneficiaryMoney())
                );
            }

            if ("spvisit".equalsIgnoreCase(type) && costBeneficiary.costBeneficiaryQut() != null) {
                try {
                    insurance.setSpecialistVisitsLimit(
                            Integer.parseInt(costBeneficiary.costBeneficiaryQut().trim())
                    );
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private String buildBenefitsJson(EligibilityCoverageDTO coverage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", coverage.items());
        payload.put("costBeneficiaries", coverage.costBeneficiaries());
        payload.put("classList", coverage.classList());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private BigDecimal extractAnnualPolicyLimit(EligibilityCoverageDTO coverage) {
        if (!(coverage.items() instanceof Map<?, ?> rawItems)) {
            return null;
        }

        Object healthBenefitItems = rawItems.get("Health Benefit Plan Coverage.");
        if (!(healthBenefitItems instanceof List<?> itemList)) {
            return null;
        }

        for (Object itemObject : itemList) {
            if (!(itemObject instanceof Map<?, ?> itemMap)) {
                continue;
            }

            Object descriptionObject = itemMap.get("description");
            String description = descriptionObject == null ? "" : descriptionObject.toString().toLowerCase();

            if (!description.contains("policy maximum annual limit")) {
                continue;
            }

            Object benefitsObject = itemMap.get("benefits");
            if (!(benefitsObject instanceof List<?> benefits)) {
                continue;
            }

            for (Object benefitObject : benefits) {
                if (!(benefitObject instanceof Map<?, ?> benefitMap)) {
                    continue;
                }

                Object typeObject = benefitMap.get("type");
                if (typeObject != null && "Benefit".equalsIgnoreCase(typeObject.toString().trim())) {
                    return toBigDecimal(benefitMap.get("value"));
                }
            }
        }

        return null;
    }

    private void setIfBlank(Consumer<String> setter, String currentValue, String newValue) {
        if (isBlank(currentValue) && !isBlank(newValue)) {
            setter.accept(newValue.trim());
        }
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        if (!isBlank(second)) {
            return second.trim();
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }

        String date = value.trim();

        try {
            if (date.length() >= 10) {
                return LocalDate.parse(date.substring(0, 10));
            }
        } catch (RuntimeException ignored) {
        }

        try {
            return LocalDate.parse(date);
        } catch (RuntimeException ignored) {
        }

        try {
            return LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
