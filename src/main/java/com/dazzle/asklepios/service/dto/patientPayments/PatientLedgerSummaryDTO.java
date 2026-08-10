
package com.dazzle.asklepios.service.dto.patientPayments;

import java.math.BigDecimal;

public record PatientLedgerSummaryDTO(
        Long patientId,
        BigDecimal totalDebt,
        BigDecimal insuranceOutstandingAmount,
        BigDecimal walletBalance,
        BigDecimal reservedBalance,
        BigDecimal consumedAmount
) {}