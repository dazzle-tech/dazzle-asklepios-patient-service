package com.dazzle.asklepios.integration.waseel.dto.approval;

public record ApprovalCancelRequest(
        String approvalRequestId,
        String cancelReason
) {}