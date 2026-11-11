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

        /**
         * Default procedures of the visit department/specialty. Each one is
         * ordered on the encounter and billed with the default services.
         */
        List<@Valid PrepareDefaultProcedureItem> procedures,

        @NotNull
        @Size(max = 150)
        String requestId,

        /**
         * When true, reception confirmed payment with collect-zero / deferred collection.
         * The encounter advances to WAITING_TRIAGE (or NEW) after services are prepared.
         */
        Boolean payZeroNow,

        Boolean acceptUncoveredAsCash,

        /**
         * Optional default-service discount. All three are required together.
         * Setup-service checks the code, the user, and the authorization token
         * before the price is changed.
         */
        @Size(max = 50)
        String discountCode,

        Long authorizedUserId,

        @Size(max = 300)
        String discountAuthorizationToken

) implements Serializable {

    public List<PrepareDefaultProcedureItem> defaultProcedures() {
        return procedures == null ? List.of() : procedures;
    }
}
