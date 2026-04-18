package com.dazzle.asklepios.service.dto.patientServiceProduct;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record PatientServiceProductUpdateDTO(
        @NotNull Long id,
        @NotNull BillingItemTypes billingItemType,

        Long brandMedicationId,
        Long diagnosticTestId,
        Long serviceId,
        Long procedureId,

        @NotNull Long quantity,
        @NotNull BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal exemptionAmount,
        BigDecimal taxAmount,
        @NotNull BigDecimal totalAmount,
        @NotNull Currency currency,
        String notes,

        Boolean isBilled,
        Long billingInvoiceId,
        Long billingInvoiceItemId
) implements Serializable {}