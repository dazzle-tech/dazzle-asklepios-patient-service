package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

import java.time.Instant;

public record PreAuthorizationSearchRequest(
        Long patientId,
        Long patientInsuranceId,
        Instant serviceDate,
        String status,
        String preAuthReferenceNo
) {}