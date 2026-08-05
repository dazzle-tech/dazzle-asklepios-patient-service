package com.dazzle.asklepios.integration.waseel.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class InsuranceCoverage {

    private BigDecimal copaymentPercent;
    private BigDecimal copaymentCap;

}