package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record FreezeEligibilitySnapshotRequest(

        @NotBlank
        String requestId,

        Long patientInsuranceId

) implements Serializable {
}
