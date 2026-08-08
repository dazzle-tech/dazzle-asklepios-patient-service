package com.dazzle.asklepios.integration.waseel.dto;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;

import java.util.List;

public record EncounterPreAuthorizationRefreshResponse(
        Long encounterId,
        int refreshedRequestCount,
        int approvedItemCount,
        int rejectedItemCount,
        int pendingItemCount,
        boolean canCloseCalculation,
        String message,
        List<RefreshedPreAuthorizationItem> items
) {
    public record RefreshedPreAuthorizationItem(
            Long patientServiceProductId,
            Long preAuthorizationRequestId,
            Long approvalRequestId,
            PreAuthorizationStatus preAuthorizationStatus,
            String waseelStatus,
            boolean canPayAsCash,
            boolean canClonePreAuthorization
    ) {}
}
