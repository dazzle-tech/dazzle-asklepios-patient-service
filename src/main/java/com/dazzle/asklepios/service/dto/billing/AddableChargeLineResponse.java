package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;

import java.io.Serializable;
import java.math.BigDecimal;

public record AddableChargeLineResponse(

        Long chargeLineId,

        Long patientServiceProductId,

        String itemCode,

        String itemDescription,

        BigDecimal quantity,

        BigDecimal unitPrice,

        BigDecimal netAmount,

        BigDecimal patientShareAmount,

        BigDecimal insuranceShareAmount,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal taxAmount,

        Currency currency

) implements Serializable {
}
