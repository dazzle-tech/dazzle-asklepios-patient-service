package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityCostBeneficiaryDTO(
        String costBeneficiaryType,
        String costBeneficiaryQut,
        Integer costBeneficiaryMoney,
        List<Object> exceptionList
) {}