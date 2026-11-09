package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ServiceCancellationBeforeInvoiceRequest(

        @NotNull
        Long patientServiceProductId,

        @NotBlank
        String reason

) implements Serializable {
}
