package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.PayorPlanCoverageClassDTO;
import com.dazzle.asklepios.client.setup.dto.PayorPlanDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCoverageClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalInsurancePlanDTO;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ApprovalInsurancePlanMapper {

    private ApprovalInsurancePlanMapper() {}

    public static ApprovalInsurancePlanDTO buildInsurancePlan(
            PatientInsurance insurance,
            PayorDTO payor,
            PayorPlanDTO plan,
            List<ApprovalCoverageClassDTO> coverageClasses
    ) {
        return new ApprovalInsurancePlanDTO(
                resolvePlanId(plan),
                firstNonBlank(
                        insurance.getPayerNphiesId(),
                        plan.payerNphiesId(),
                        payor.nphiesId(),
                        payor.waseelPayerId()
                ),
                safe(insurance.getMemberCardId()),
                safe(insurance.getPolicyNumber()),
                resolvePolicyHolder(insurance),
                insurance.getMaxLimit(),
                insurance.getPatientShare(),
                firstNonBlank(
                        insurance.getCoverageType(),
                        plan.coverageType()
                ),
                coverageClasses,
                safe(insurance.getRelationWithSubscriber()),
                insurance.getExpirationDate() != null
                        ? insurance.getExpirationDate().format(DateTimeFormatter.ISO_DATE)
                        : null,
                safe(payor.name()),
                firstNonBlank(
                        insurance.getPayerNphiesId(),
                        plan.payerNphiesId(),
                        payor.nphiesId()
                ),
                Boolean.TRUE.equals(insurance.getIsPrimary()),
                safe(payor.tpaNphiesId())
        );
    }

    public static List<ApprovalCoverageClassDTO> mapCoverageClasses(
            List<PayorPlanCoverageClassDTO> classes
    ) {
        if (classes == null) return List.of();
        return classes.stream()
                .map(c -> new ApprovalCoverageClassDTO(
                        normalizeCoverageClassType(c.coverageClassType()),
                        safe(c.coverageClassValue()),
                        safe(c.coverageClassName())
                ))
                .toList();
    }

    // ─── الإصلاح الرئيسي ───────────────────────────────────────────────
    private static String resolvePolicyHolder(PatientInsurance insurance) {
        // أولاً: استخدم policyHolderId لو موجود
        if (insurance.getPolicyHolderId() != null) {
            return insurance.getPolicyHolderId().toString();
        }
        // ثانياً: استخدم policyNumber كـ fallback (مو الاسم!)
        if (insurance.getPolicyNumber() != null && !insurance.getPolicyNumber().isBlank()) {
            return insurance.getPolicyNumber();
        }
        // أخيراً: memberCardId
        return safe(insurance.getMemberCardId());
    }

    private static Object resolvePlanId(PayorPlanDTO plan) {
        if (plan.waseelPlanId() != null && !plan.waseelPlanId().isBlank()) {
            return plan.waseelPlanId();
        }
        return plan.id();
    }

    private static String normalizeCoverageClassType(String type) {
        if (type == null || type.isBlank()) return "";
        return type.trim().toLowerCase().replace("_", "-");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}