package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record BillingEligibilitySnapshotResponse(

        Long id,

        Long encounterId,

        Long patientId,

        Long patientInsuranceId,

        Long waseelEligibilityRequestId,

        String eligibilityResponseId,

        String memberId,

        String policyNumber,

        String policyHolder,

        String network,

        String coverageStatus,

        String inforce,

        BigDecimal copaymentPercent,

        BigDecimal copaymentCap,

        Instant frozenAt,

        String frozenBy,

        boolean frozen

) implements Serializable {
}
