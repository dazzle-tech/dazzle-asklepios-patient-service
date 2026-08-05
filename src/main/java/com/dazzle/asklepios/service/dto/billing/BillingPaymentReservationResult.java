package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingPaymentReservationResult(

        Long patientServiceProductId,

        Long reservationId,

        String reservationNumber,

        String itemDescription,

        String billingItemType,

        BigDecimal patientResponsibilityAmount,

        BigDecimal reservedAmount,

        BigDecimal uncoveredAmount,

        boolean fullyReserved

) implements Serializable {
}
