package com.dazzle.asklepios.service.dto.billing;

import java.math.BigDecimal;

/**
 * Code discount taken off one default service or procedure during pricing.
 */
public record DefaultServiceDiscountLine(
        Long patientServiceProductId,
        Long serviceId,
        Long procedureId,
        BigDecimal grossAmount,
        BigDecimal discountAmount
) {
}
