package com.dazzle.asklepios.integration.waseel.dto.approval;

import java.math.BigDecimal;
import java.util.List;

public record ApprovalInsurancePlanDTO(
        Object planId,
        String payerId,
        String memberCardId,
        String policyNumber,
        String policyHolder,
        BigDecimal maxLimit,
        BigDecimal patientShare,
        String coverageType,
        List<ApprovalCoverageClassDTO> coverageClass,
        String relationWithSubscriber,
        String expiryDate,
        String payerName,
        String payerNphiesId,
        Boolean primary,
        String tpaNphiesId
) {}