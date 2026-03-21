package com.dazzle.asklepios.service.dto.patientPayments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPaymentServiceItemDTO(
        @NotNull Long serviceId,
        @NotNull BigDecimal price,
        @NotNull Boolean isExempted
) implements Serializable { }
