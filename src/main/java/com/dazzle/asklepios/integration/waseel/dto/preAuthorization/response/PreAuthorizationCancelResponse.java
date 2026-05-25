package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PreAuthorizationCancelResponse(
        Long transactionId,
        String status,
        String message,
        Long outgoingTransactionId,
        String outcome,
        OffsetDateTime transactionLogDate,
        Long approvalRequestId,
        String statusReason,
        List<Object> errors
) {}
