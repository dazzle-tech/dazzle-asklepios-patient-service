package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.PayorPlanClient;
import com.dazzle.asklepios.client.setup.PayorPlanCoverageClassClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.PayorPlanCoverageClassDTO;
import com.dazzle.asklepios.client.setup.dto.PayorPlanDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCoverageClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalInsurancePlanDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApprovalInsurancePlanMapper {

    private final PayorClient payorClient;
    private final PayorPlanClient payorPlanClient;
    private final PayorPlanCoverageClassClient coverageClassClient;

    public ApprovalInsurancePlanDTO buildInsurancePlan(PatientInsurance insurance) {
        if (insurance == null) {
            throw new BadRequestAlertException(
                    "Patient insurance is required",
                    "preAuthorization",
                    "insurance.required"
            );
        }

        PayorDTO payor = getPayor(insurance.getPayorId());
        PayorPlanDTO plan = getPayorPlan(insurance.getPlanId());

        List<ApprovalCoverageClassDTO> coverageClasses = getCoverageClasses(plan.id());

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

    private PayorDTO getPayor(Long payorId) {
        if (payorId == null) {
            throw new BadRequestAlertException(
                    "Payor is required",
                    "preAuthorization",
                    "payor.required"
            );
        }

        try {
            return payorClient.getPayorById(payorId);
        } catch (FeignException.NotFound e) {
            throw new BadRequestAlertException(
                    "Payor not found in setup",
                    "preAuthorization",
                    "payor.notFound"
            );
        }
    }

    private PayorPlanDTO getPayorPlan(Long planId) {
        if (planId == null) {
            throw new BadRequestAlertException(
                    "Payor plan is required",
                    "preAuthorization",
                    "plan.required"
            );
        }

        try {
            return payorPlanClient.getPayorPlanById(planId);
        } catch (FeignException.NotFound e) {
            throw new BadRequestAlertException(
                    "Payor plan not found in setup",
                    "preAuthorization",
                    "plan.notFound"
            );
        }
    }

    private List<ApprovalCoverageClassDTO> getCoverageClasses(Long planId) {
        if (planId == null) {
            return List.of();
        }

        try {
            return coverageClassClient.getActiveCoverageClassesByPlan(planId)
                    .stream()
                    .map(c -> new ApprovalCoverageClassDTO(
                            normalizeCoverageClassType(c.coverageClassType()),
                            safe(c.coverageClassValue()),
                            safe(c.coverageClassName())
                    ))
                    .toList();
        } catch (FeignException.NotFound e) {
            return List.of();
        }
    }

    private Object resolvePlanId(PayorPlanDTO plan) {
        if (plan.waseelPlanId() != null && !plan.waseelPlanId().isBlank()) {
            return plan.waseelPlanId();
        }

        return plan.id();
    }

    private String resolvePolicyHolder(PatientInsurance insurance) {
        if (insurance.getPolicyHolderId() != null) {
            return insurance.getPolicyHolderId().toString();
        }

        return safe(insurance.getPolicyHolderName());
    }

    private String normalizeCoverageClassType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }

        return type.trim().toLowerCase().replace("_", "-");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}