package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record BillingRuleEvaluationRequest(

        @NotNull
        BillingItemTypes billingItemType,

        @NotNull
        BillingEventType billingEvent,

        Long serviceId,

        Long procedureId,

        Long diagnosticTestId,

        Long brandMedicationId

) implements Serializable {
}
