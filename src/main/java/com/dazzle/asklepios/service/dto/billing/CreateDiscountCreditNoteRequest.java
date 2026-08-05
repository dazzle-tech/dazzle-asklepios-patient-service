package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.DiscountCreditScope;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;

public record CreateDiscountCreditNoteRequest(

        @NotNull
        DiscountCreditScope scope,

        /** Required when scope = LINE. */
        Long documentItemId,

        /** Fixed discount amount (use amount or percent, not both). */
        @DecimalMin(value = "0.01", inclusive = true)
        BigDecimal discountAmount,

        /** Percentage of the line or invoice remaining balance. */
        @DecimalMin(value = "0.01", inclusive = true)
        @DecimalMax(value = "100.00", inclusive = true)
        BigDecimal discountPercent,

        @Size(max = 500)
        String reason

) implements Serializable {
}
