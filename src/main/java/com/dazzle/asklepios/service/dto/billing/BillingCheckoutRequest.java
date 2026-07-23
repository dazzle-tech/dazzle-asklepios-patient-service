package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingCheckoutRequest(

        @NotNull
        Long chargeId,

        /*
         * Whether the checkout may create patient debt
         * for any patient outstanding amount.
         */
        @NotNull
        Boolean allowDebit,

        /*
         * Credit limit resolved from Billing Configuration.
         */
        @DecimalMin("0.0000")
        BigDecimal creditLimit,

        Boolean debitApprovalRequired,

        @Size(max = 50)
        String approvedBy,

        LocalDate debitDueDate,

        @NotBlank
        @Size(max = 50)
        String checkoutBy,

        @NotBlank
        @Size(max = 100)
        String requestId,

        @NotNull
        BillingLedgerSourceChannel sourceChannel

) implements Serializable {
}