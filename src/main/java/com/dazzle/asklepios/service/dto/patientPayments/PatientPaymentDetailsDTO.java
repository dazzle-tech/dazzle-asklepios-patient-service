package com.dazzle.asklepios.service.dto.patientPayments;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;

import java.math.BigDecimal;
import java.util.List;

public record PatientPaymentDetailsDTO(
        Long id,
        Long patientId,
        Long encounterId,
        BigDecimal dueAmount,
        BigDecimal patientBalance,
        BigDecimal amountPaid,
        BigDecimal remaining,
        BigDecimal refunds,
        BigDecimal paidFromAmount,
        BigDecimal paidFromBalance,
        List<PatientServiceAndProduct> services
) {}