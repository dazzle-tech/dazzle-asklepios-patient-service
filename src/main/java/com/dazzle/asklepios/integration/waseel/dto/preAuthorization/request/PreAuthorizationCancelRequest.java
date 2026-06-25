package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CancelReason;

public record PreAuthorizationCancelRequest(
        String approvalRequestId,
        CancelReason cancelReason
) {}