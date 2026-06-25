package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

public record PreAuthorizationCancelRequest(
        String approvalRequestId,
        String cancelReason
) {}