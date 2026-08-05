package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record FinancialCloseRequest(

        @NotBlank
        String requestId

) implements Serializable {
}
