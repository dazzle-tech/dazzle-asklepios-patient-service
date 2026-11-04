package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record RegistrationPendingPaymentItemDTO(
        Long patientServiceProductId,
        Long patientId,
        Long encounterId,
        String encounterNumber,
        String patientName,
        Map<String, Object> patient,
        Long bedId,
        String bedName,
        ServiceSource serviceSource,
        String billingItemType,
        String itemName,
        BigDecimal patientShareAmount,
        BigDecimal insuranceShareAmount,
        BigDecimal patientRemainingAmount,
        Currency currency,
        PaymentStatus paymentStatus,
        String displayPaymentStatus,
        boolean amountsAvailable,
        boolean collectible,
        Instant createdDate
) implements Serializable {
}
