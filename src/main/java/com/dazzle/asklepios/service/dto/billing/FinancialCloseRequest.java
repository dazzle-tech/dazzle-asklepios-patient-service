package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record FinancialCloseRequest(

        @NotBlank
        String requestId,

        Long facilityId,

        /** Why no laboratory or radiology investigation was done, asked when a Waseel insurance visit has none. */
        String investigationNotPerformedReason

) implements Serializable {
}
