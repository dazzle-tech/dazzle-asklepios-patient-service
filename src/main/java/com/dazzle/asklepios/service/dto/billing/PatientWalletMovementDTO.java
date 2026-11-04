package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record PatientWalletMovementDTO(

        Long ledgerId,

        Instant transactionDate,

        String movement,

        String transactionType,

        BigDecimal amount,

        Currency currency,

        BigDecimal balanceAfter,

        String description,

        String reason,

        String receiptNumber,

        String paymentNumber,

        String paymentCategory,

        String paymentMethod,

        String encounterNumber,

        Long encounterId,

        String serviceName,

        String serviceCode,

        String referenceNumber,

        String notes

) implements Serializable {
}
