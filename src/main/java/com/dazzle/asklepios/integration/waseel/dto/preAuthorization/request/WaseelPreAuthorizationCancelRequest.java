package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

public record WaseelPreAuthorizationCancelRequest(
        String approvalRequestId,
        String cancelReason
) {}