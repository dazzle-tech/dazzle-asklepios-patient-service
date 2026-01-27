package com.dazzle.asklepios.web.rest.vm.insuranceCoverage;

import com.dazzle.asklepios.domain.PatientInsuranceCoverage;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.InsuranceCoverageType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientInsuranceCoverageResponseVM(
        Long id,
        Long insuranceId,
        BillingItemTypes itemType,
        InsuranceCoverageType coverageType,
        BigDecimal amount,
        Instant createdDate,
        String createdBy,
        Instant lastModifiedDate,
        String lastModifiedBy
) implements Serializable {

    public static PatientInsuranceCoverageResponseVM ofEntity(PatientInsuranceCoverage coverage) {
        return new PatientInsuranceCoverageResponseVM(
                coverage.getId(),
                coverage.getInsurance() != null ? coverage.getInsurance().getId() : null,
                coverage.getItemType(),
                coverage.getCoverageType(),
                coverage.getAmount(),
                coverage.getCreatedDate(),
                coverage.getCreatedBy(),
                coverage.getLastModifiedDate(),
                coverage.getLastModifiedBy()
        );
    }
}
