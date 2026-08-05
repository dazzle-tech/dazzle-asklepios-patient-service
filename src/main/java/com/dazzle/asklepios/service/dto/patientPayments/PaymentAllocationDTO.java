package com.dazzle.asklepios.service.dto.patientPayments;

import java.math.BigDecimal;

public record PaymentAllocationDTO(
        Long documentItemId,
        BigDecimal amount
) {}
