package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingCancellationReason;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public record BillingCancellationRequest(

        @NotNull
        Long patientServiceProductId,

        @NotNull
        BillingCancellationReason cancellationReason,

        @NotBlank
        @Size(max = 500)
        String reason,

        @NotBlank
        @Size(max = 50)
        String cancelledBy,

        @NotBlank
        @Size(max = 100)
        String requestId,

        @NotNull
        BillingLedgerSourceChannel sourceChannel

) implements Serializable {
}