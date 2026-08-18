package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/**
 * Prepares selected encounter default services before treatment starts.
 *
 * Currency is the facility currency resolved by the calling application;
 * it must not be an editable cashier field.
 */
public record PrepareDefaultServicesRequest(

        @NotNull
        Long patientId,

        @NotNull
        Long facilityId,

        @NotNull
        Currency currency,

        @NotNull
        BillingCoverageType coverageType,

        Long patientInsuranceId,

        @NotNull
        List<@Valid PrepareDefaultServiceItem> items,

        @NotNull
        @Size(max = 150)
        String requestId,

        /**
         * When true, reception confirmed payment with collect-zero / deferred collection.
         * The encounter advances to WAITING_TRIAGE (or NEW) after services are prepared.
         */
        Boolean payZeroNow,

        Boolean acceptUncoveredAsCash

) implements Serializable {
}
