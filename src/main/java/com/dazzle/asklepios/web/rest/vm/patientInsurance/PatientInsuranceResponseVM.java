package com.dazzle.asklepios.web.rest.vm.patientInsurance;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientInsuranceResponseVM(
        Long id,
        Long patientId,
        Long payorId,
        Long planId,
        Long policyHolderId,
        BigDecimal policyNumber,
        BigDecimal groupNumber,
        LocalDate expirationDate,
        BigDecimal remainingBenefits,
        BigDecimal remainingDeductibles,
        Boolean isPrimary,
        Instant createdDate,
        String createdBy,
        Instant lastModifiedDate,
        String lastModifiedBy
) implements Serializable {

    public static PatientInsuranceResponseVM ofEntity(PatientInsurance insurance) {
        return new PatientInsuranceResponseVM(
                insurance.getId(),
                insurance.getPatient() != null ? insurance.getPatient().getId() : null,
                insurance.getPayorId(),
                insurance.getPlanId(),
                insurance.getPolicyHolderId(),
                insurance.getPolicyNumber(),
                insurance.getGroupNumber(),
                insurance.getExpirationDate(),
                insurance.getRemainingBenefits(),
                insurance.getRemainingDeductibles(),
                insurance.getIsPrimary(),
                insurance.getCreatedDate(),
                insurance.getCreatedBy(),
                insurance.getLastModifiedDate(),
                insurance.getLastModifiedBy()
        );
    }
}
