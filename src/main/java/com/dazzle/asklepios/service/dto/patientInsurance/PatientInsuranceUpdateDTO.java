package com.dazzle.asklepios.service.dto.patientInsurance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientInsuranceUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long payorId,
        @NotNull Long planId,
        Long policyHolderId,
        @NotNull Long policyNumber,
        Long groupNumber,
        @NotNull @Future LocalDate expirationDate,
        BigDecimal remainingBenefits,
        BigDecimal remainingDeductibles,
        Boolean isPrimary
) implements Serializable {
}
