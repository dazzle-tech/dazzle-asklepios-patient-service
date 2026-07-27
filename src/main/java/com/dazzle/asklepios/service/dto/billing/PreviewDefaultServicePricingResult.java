package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record PreviewDefaultServicePricingResult(

        Long serviceId,

        Integer sequence,

        BigDecimal setupUnitPrice,

        BigDecimal unitPrice,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        String priceSource,

        String priceListItemCode

) implements Serializable {
}
