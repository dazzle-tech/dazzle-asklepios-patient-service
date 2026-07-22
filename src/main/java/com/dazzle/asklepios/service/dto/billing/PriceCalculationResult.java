package com.dazzle.asklepios.service.dto.billing;

import java.math.BigDecimal;

public record PriceCalculationResult(

        BigDecimal quantity,

        BigDecimal unitPrice,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal exemptionAmount,

        BigDecimal taxableAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount

) {
}