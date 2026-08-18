package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record InsurancePriceListCoverageCheckRequest(

        @NotNull
        Long encounterId,

        @NotNull
        BillingItemTypes billingItemType,

        Long serviceId,

        Long procedureId,

        Long diagnosticTestId,

        Long brandMedicationId,

        Currency currency

) implements Serializable {
}
