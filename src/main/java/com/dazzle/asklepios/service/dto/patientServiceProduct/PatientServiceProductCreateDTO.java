package com.dazzle.asklepios.service.dto.patientServiceProduct;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
@JsonIgnoreProperties(ignoreUnknown = true)
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

        @NotNull Currency currency,
        @NotNull ServiceSource serviceSource,
        Long sourceId,

        String notes,

        Boolean acceptUncoveredAsCash
) implements Serializable {}