package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CancelReason;

public record PreAuthorizationCancelRequest(
        Long preAuthorizationId,
        Long approvalRequestId,
        CancelReason cancelReason
) {}