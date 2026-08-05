package com.dazzle.asklepios.service.dto;

import java.math.BigDecimal;

public record InsuranceSplit(
        BigDecimal patientShare,
        BigDecimal insuranceShare
) {}