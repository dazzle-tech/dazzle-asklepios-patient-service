package com.dazzle.asklepios.integration.waseel.dto.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApprovalResponse(
        Long transactionId,
        Long responseId,
        String outgoingTransactionId,
        String approvalRequestId,
        String status,
        String outcome,
        String disposition,
        Object errors
) {}