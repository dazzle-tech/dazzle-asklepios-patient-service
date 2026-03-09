package com.dazzle.asklepios.service.dto.patientPayments;

import com.dazzle.asklepios.domain.PatientPaymentServices;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentMethods;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PatientPaymentFormDTO(

        Long id,
        Long patientId,
        Long encounterId,
        Long planId,

        PaymentTypes paymentTypes,
        PaymentMethods paymentMethods,

        BigDecimal amount,
        Currency currency,
        Currency facilityDefaultCurrency,
        BigDecimal exchangeRate,
        BigDecimal amountInFacilityCurrency,

        BigDecimal dueAmount,
        BigDecimal patientBalance,
        BigDecimal remaining,
        BigDecimal refunds,
        BigDecimal paidFromAmount,
        BigDecimal paidFromBalance,

        Boolean addToFreeBalance,
        Boolean useBalanceToSettleDebts,

        String cardNumber,
        String cardHolderName,
        LocalDate cardValidUntil,

        String chequeNumber,
        String chequeBankName,
        LocalDate chequeDueDate,

        String transferNumber,
        String transferBankName,
        LocalDate transferDate,

        List<PatientPaymentServices> services

) {}