package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentItemAdjustmentAction;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record InvoiceLineAdjustmentRequest(

        @NotNull
        FinancialDocumentItemAdjustmentAction action,

        Long documentItemId,

        Long chargeLineId,

        @DecimalMin(value = "0.01", inclusive = true)
        BigDecimal amount,

        @DecimalMin(value = "0.01", inclusive = true)
        BigDecimal quantity,

        @DecimalMin(value = "0.01", inclusive = true)
        BigDecimal unitPrice,

        BillingItemTypes billingItemType,

        Long brandMedicationId,

        Long diagnosticTestId,

        Long serviceId,

        Long procedureId,

        Currency currency,

        ServiceSource serviceSource,

        Long sourceId,

        String notes

) implements Serializable {
}
