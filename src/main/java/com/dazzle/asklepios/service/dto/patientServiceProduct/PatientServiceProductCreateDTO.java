package com.dazzle.asklepios.service.dto.patientServiceProduct;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record PatientServiceProductCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull BillingItemTypes billingItemType,

        Long brandMedicationId,
        Long diagnosticTestId,
        Long serviceId,
        Long procedureId,

        @NotNull Long quantity,
        @NotNull BigDecimal unitPrice,
        @NotBlank String currency,
        String notes
) implements Serializable {}