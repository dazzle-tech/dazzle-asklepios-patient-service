package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

public record CreateFinancialDocumentAdjustmentRequest(

        @Size(max = 500)
        String reason,

        @NotEmpty
        @Valid
        List<InvoiceLineAdjustmentRequest> lines

) implements Serializable {
}
