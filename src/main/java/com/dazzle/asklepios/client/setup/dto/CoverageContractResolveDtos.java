package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CoverageContractResolveDtos {

    private CoverageContractResolveDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(
            Long insurancePayerId,
            String payerNphiesId,
            Long tpaId,
            String tpaName,
            String policyNumber,
            String className,
            String encounterType,
            LocalDate asOfDate,
            Long facilityId,
            Long departmentId,
            List<Long> diagnosisIds,
            String billingItemType,
            Long catalogItemId
    ) {
        public Request(
                Long insurancePayerId,
                String payerNphiesId,
                Long tpaId,
                String tpaName,
                String policyNumber,
                String className,
                String encounterType,
                LocalDate asOfDate
        ) {
            this(
                    insurancePayerId,
                    payerNphiesId,
                    tpaId,
                    tpaName,
                    policyNumber,
                    className,
                    encounterType,
                    asOfDate,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            boolean matched,
            String matchReason,
            ContractSnapshot contract,
            CopaymentSnapshot copayment,
            Boolean coverageConfigured,
            Boolean uncovered,
            CoverageReadingSnapshot coverage,
            CoverageReadingSnapshot limit,
            CoverageReadingSnapshot cashLimit,
            DiscountSnapshot discount,
            ExclusionSnapshot exclusion,
            PreApprovalSnapshot preApproval
    ) {
        public Response(
                boolean matched,
                String matchReason,
                ContractSnapshot contract,
                CopaymentSnapshot copayment
        ) {
            this(matched, matchReason, contract, copayment, false, false, null, null, null, null, null, null);
        }

        public Response(
                boolean matched,
                String matchReason,
                ContractSnapshot contract,
                CopaymentSnapshot copayment,
                Boolean coverageConfigured,
                Boolean uncovered,
                CoverageReadingSnapshot coverage
        ) {
            this(matched, matchReason, contract, copayment, coverageConfigured, uncovered, coverage, null, null, null, null, null);
        }

        public Response(
                boolean matched,
                String matchReason,
                ContractSnapshot contract,
                CopaymentSnapshot copayment,
                Boolean coverageConfigured,
                Boolean uncovered,
                CoverageReadingSnapshot coverage,
                CoverageReadingSnapshot limit
        ) {
            this(matched, matchReason, contract, copayment, coverageConfigured, uncovered, coverage, limit, null, null, null, null);
        }

        public Response(
                boolean matched,
                String matchReason,
                ContractSnapshot contract,
                CopaymentSnapshot copayment,
                Boolean coverageConfigured,
                Boolean uncovered,
                CoverageReadingSnapshot coverage,
                CoverageReadingSnapshot limit,
                CoverageReadingSnapshot cashLimit
        ) {
            this(matched, matchReason, contract, copayment, coverageConfigured, uncovered, coverage, limit, cashLimit, null, null, null);
        }

        public Response(
                boolean matched,
                String matchReason,
                ContractSnapshot contract,
                CopaymentSnapshot copayment,
                Boolean coverageConfigured,
                Boolean uncovered,
                CoverageReadingSnapshot coverage,
                CoverageReadingSnapshot limit,
                CoverageReadingSnapshot cashLimit,
                DiscountSnapshot discount
        ) {
            this(matched, matchReason, contract, copayment, coverageConfigured, uncovered, coverage, limit, cashLimit, discount, null, null);
        }

        public Response(
                boolean matched,
                String matchReason,
                ContractSnapshot contract,
                CopaymentSnapshot copayment,
                Boolean coverageConfigured,
                Boolean uncovered,
                CoverageReadingSnapshot coverage,
                CoverageReadingSnapshot limit,
                CoverageReadingSnapshot cashLimit,
                DiscountSnapshot discount,
                ExclusionSnapshot exclusion
        ) {
            this(matched, matchReason, contract, copayment, coverageConfigured, uncovered, coverage, limit, cashLimit, discount, exclusion, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContractSnapshot(
            Long id,
            String guarantorType,
            Long companyId,
            String companyName,
            String code,
            String policyNumber,
            String coverageBasis,
            Long insurancePayerId,
            String insurancePayerName,
            Long priceListSetupId,
            String priceListName,
            LocalDate startDate,
            LocalDate endDate,
            Long classId,
            String className,
            Boolean isActive
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CopaymentSnapshot(
            Long id,
            String encounterType,
            String valueType,
            BigDecimal valueAmount,
            Boolean discountOnExcluded,
            Boolean discountOnCash,
            Boolean discountOnExceededCash
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CoverageReadingSnapshot(
            Long termId,
            Long itemId,
            String categoryScope,
            String billingItemType,
            Long serviceId,
            String valueType,
            BigDecimal limitValue,
            String periodBasis,
            String coverageBasis
    ) {
        public CoverageReadingSnapshot(
                Long termId,
                Long itemId,
                String categoryScope,
                String billingItemType,
                Long serviceId,
                String valueType,
                BigDecimal limitValue
        ) {
            this(termId, itemId, categoryScope, billingItemType, serviceId, valueType, limitValue, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscountSnapshot(
            Long id,
            String targetType,
            String billingItemType,
            Long serviceId,
            String encounterType,
            String discountType,
            BigDecimal discountValue
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExclusionSnapshot(
            Long id,
            String exclusionType,
            String billingItemType,
            Long serviceId,
            Boolean allDiagnoses,
            Long diagnosisId,
            String encounterType,
            String excludedResult
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreApprovalSnapshot(
            Long preApprovalId,
            Long itemId,
            String approvalScope,
            Long facilityId,
            Long departmentId,
            String encounterType,
            String itemType,
            String serviceCategory,
            Long serviceId,
            Boolean allDiagnoses,
            Long diagnosisId
    ) {}
}
