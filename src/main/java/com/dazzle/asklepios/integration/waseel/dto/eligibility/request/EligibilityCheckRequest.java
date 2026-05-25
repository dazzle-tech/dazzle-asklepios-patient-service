package com.dazzle.asklepios.integration.waseel.dto.eligibility.request;

import java.time.LocalDate;

public record EligibilityCheckRequest(
        Long patientId,
        Long patientInsuranceId,
        LocalDate serviceDate,
        Boolean benefits,
        Boolean discovery,
        Boolean validation,
        Boolean transfer,
        Boolean emergency,
        String destinationId
) {
}