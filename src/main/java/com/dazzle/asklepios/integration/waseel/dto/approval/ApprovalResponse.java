package com.dazzle.asklepios.integration.waseel.dto.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApprovalResponse(

        Long transactionId,
        String status,
        String message,

        String outgoingTransactionId,

        Long approvalRequestId,
        Long approvalResponseId,

        String preAuthRefNo,

        String outcome,
        String disposition,
        String statusReason,

        Object errors
) {}