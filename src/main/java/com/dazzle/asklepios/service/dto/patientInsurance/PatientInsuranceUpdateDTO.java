package com.dazzle.asklepios.service.dto.patientInsurance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

        @Size(max = 100)
        String memberCardId,

        @NotBlank
        @Size(max = 100)
        String policyNumber,

        @Size(max = 100)
        String groupNumber,

        @Size(max = 100)
        String payerNphiesId,

        @Size(max = 100)
        String networkId,

        @Size(max = 100)
        String sponsorNumber,

        @Size(max = 50)
        String coverageType,

        @Size(max = 50)
        String relationWithSubscriber,

        @Size(max = 100)
        String policyClassName,

        @Size(max = 255)
        String policyHolderName,

        LocalDate issueDate,

        @NotNull
        @Future
        LocalDate expirationDate,

        BigDecimal patientShare,

        BigDecimal maxLimit,

        Boolean waseelNewPlan,

        BigDecimal remainingBenefits,

        BigDecimal remainingDeductibles,

        Boolean isPrimary
) implements Serializable {
}