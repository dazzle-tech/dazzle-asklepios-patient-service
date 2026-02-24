package com.dazzle.asklepios.service.dto.patientPayments;

import com.dazzle.asklepios.domain.PatientPaymentServices;

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
        // NEW
        BigDecimal paidFromAmount,
        BigDecimal paidFromBalance,
        List<PatientPaymentServices> services
) {}
