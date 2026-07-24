package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/**
 * Request used to prepare selected default services for billing
 * before treatment starts.
 *
 * The frontend sends service references only.
 * Pricing is always resolved by the backend.
 */
public record PrepareDefaultServicesRequest(

        @NotNull
        Long patientId,

        @NotNull
        Long facilityId,

        @NotNull
        BillingCoverageType coverageType,

        Long patientInsuranceId,

        @NotEmpty
        List<@Valid PrepareDefaultServiceItem> items,

        @NotNull
        @Size(max = 150)
        String requestId

) implements Serializable {
}