package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCoverageClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalInsurancePlanDTO;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ApprovalInsurancePlanMapper {

    private ApprovalInsurancePlanMapper() {}

    public static ApprovalInsurancePlanDTO buildInsurancePlan(
            PatientInsurance insurance,
            List<ApprovalCoverageClassDTO> coverageClasses
    ) {
        if (insurance == null) {
            return null;
        }

        return new ApprovalInsurancePlanDTO(
                null,
                clean(insurance.getPayerNphiesId()),
                clean(insurance.getMemberCardId()),
                clean(insurance.getPolicyNumber()),
                resolvePolicyHolder(insurance),
                insurance.getMaxLimit(),
                insurance.getPatientShare(),
                clean(insurance.getCoverageType()),
                coverageClasses == null ? List.of() : coverageClasses,
                clean(insurance.getRelationWithSubscriber()),
                insurance.getExpirationDate() != null
                        ? insurance.getExpirationDate().format(DateTimeFormatter.ISO_DATE)
                        : null,
                clean(insurance.getPayerName()),
                clean(insurance.getPayerNphiesId()),
                Boolean.TRUE.equals(insurance.getIsPrimary()),
                clean(insurance.getTpaNphiesId())
        );
    }

    public static List<ApprovalCoverageClassDTO> mapCoverageClasses(PatientInsurance insurance) {
        if (insurance == null || isBlank(insurance.getPolicyClassName())) {
            return List.of();
        }

        String policyClassName = insurance.getPolicyClassName().trim();

        return List.of(
                new ApprovalCoverageClassDTO(
                        "plan",
                        policyClassName,
                        policyClassName
                )
        );
    }

    private static String resolvePolicyHolder(PatientInsurance insurance) {
        if (!isBlank(insurance.getPolicyHolderName())) {
            return insurance.getPolicyHolderName().trim();
        }

        if (!isBlank(insurance.getPolicyNumber())) {
            return insurance.getPolicyNumber().trim();
        }

        return clean(insurance.getMemberCardId());
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}