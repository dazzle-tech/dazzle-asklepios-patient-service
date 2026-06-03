package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import java.util.List;

public record PreAuthorizationCommunicationResponse(
        Long transactionId,
        String status,
        String message,
        String outcome,
        String disposition,
        String providerId,
        Long communicationId,
        Long outgoingTransactionId,
        List<Object> errors
) {}