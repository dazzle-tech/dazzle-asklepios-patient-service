package com.dazzle.asklepios.service.dto.patientPayments;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentMethods;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.validation.ValidPatientPaymentCreate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@ValidPatientPaymentCreate
public record PatientPaymentCreateDTO(

        @NotNull Long patientId,
        @NotNull Long encounterId,

        Long planId,

        PaymentTypes paymentTypes,
        PaymentMethods paymentMethods,

        BigDecimal amount,
        Currency currency,
        Currency facilityDefaultCurrency,
        BigDecimal amountInFacilityCurrency,

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

        @NotNull List<PatientPaymentServiceItemDTO> services

) implements Serializable { }