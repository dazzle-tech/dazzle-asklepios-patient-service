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

        String priceListItemCode,

        BigDecimal patientShareAmount,

        BigDecimal insuranceShareAmount,

        boolean insuranceVisit,

        boolean coveredByInsurance,

        boolean requiresCashConfirmation,

        String notCoveredReason,

        BigDecimal cashUnitPrice

) implements Serializable {
}
