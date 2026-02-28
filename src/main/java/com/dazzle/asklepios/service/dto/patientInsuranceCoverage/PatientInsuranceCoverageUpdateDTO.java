package com.dazzle.asklepios.service.dto.patientInsuranceCoverage;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.InsuranceCoverageType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientInsuranceCoverageUpdateDTO(
        @NotNull Long id,
        @NotNull Long insuranceId,
        @NotNull BillingItemTypes itemType,
        @NotNull InsuranceCoverageType coverageType,
        @NotNull BigDecimal amount
) implements Serializable {
}
