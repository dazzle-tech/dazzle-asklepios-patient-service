package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record PreviewCatalogItemPricingRequest(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        Long facilityId,

        @NotNull
        Currency currency,

        @NotNull
        BillingItemTypes billingItemType,

        Long brandMedicationId,

        Long diagnosticTestId,

        Long serviceId,

        Long procedureId,

        BigDecimal quantity,

        BillingCoverageType coverageType,

        Long patientInsuranceId,

        Long invoiceId,

        /** MEDICATION only: UOM unit to price in; null = the brand's price base unit */
        Long uomGroupUnitId,

        /** VACCINE only: the vaccine brand to price */
        Long vaccineBrandId

) implements Serializable {
}
