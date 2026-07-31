package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CchiInsurancePlan(

        String planId,

        String memberCardId,

        String policyNumber,

        String groupNumber,

        String expiryDate,

        String issueDate,

        @JsonAlias({"primary", "isPrimary"})
        Object isPrimary,

        String payerId,

        String payerName,

        String payerNphiesId,

        String tpaNphiesId,

        String relationWithSubscriber,

        String coverageType,

        BigDecimal patientShare,

        BigDecimal maxLimit,

        String networkId,

        String sponsorNumber,

        String policyClassName,

        String policyHolder,

        @JsonAlias({"coverageClassList", "coverageClass", "classList"})
        List<CchiCoverageClass> coverageClassList,

        @JsonAlias({"newPlan", "isNewPlan"})
        Object newPlan

) implements Serializable {
}