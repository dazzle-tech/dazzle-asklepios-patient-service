package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;

import java.io.Serializable;
import java.util.List;

/**
 * Result returned after preparing selected default services
 * and passing them through the Billing Engine.
 */
public record PrepareDefaultServicesResult(

        Long patientId,

        Long encounterId,

        Long facilityId,

        BillingCoverageType coverageType,

        Long patientInsuranceId,

        List<PreparedDefaultServiceResult> items,

        boolean processed,

        String message

) implements Serializable {

    public record PreparedDefaultServiceResult(

            Long patientServiceProductId,

            Long serviceId,

            Integer sequence,

            BillingOperationResult billingResult

    ) implements Serializable {
    }
}