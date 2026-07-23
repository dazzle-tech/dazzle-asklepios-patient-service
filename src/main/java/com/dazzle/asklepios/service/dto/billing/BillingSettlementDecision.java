package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.PaymentStatus;

import java.math.BigDecimal;

public record BillingSettlementDecision(

        BigDecimal patientResponsibility,

        BigDecimal insuranceResponsibility,

        BigDecimal exemptedAmount,

        BigDecimal requestedReservation,

        BigDecimal remainingPatientAmount,

        boolean exemption,

        boolean reservationRequired,

        boolean debitCandidate,

        PaymentStatus paymentStatus

) {
}