package com.dazzle.asklepios.service.dto.patientPayments;

import java.math.BigDecimal;

public record InsuranceAmountDTO(

        BigDecimal dueAmount,
        BigDecimal patientShare,
        BigDecimal insuranceShare

) {}
