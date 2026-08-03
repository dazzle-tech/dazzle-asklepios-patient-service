package com.dazzle.asklepios.integration.waseel.dto;

import java.time.Instant;
import java.util.List;

public record PreAuthorizationCommunicationHistoryResponse(
        Long trackId,
        String trackType,
        String status,
        String outcome,
        String message,
        Long communicationId,
        Long transactionId,
        Long approvalResponseId,
        Instant createdDate,
        String createdBy,
        List<CommunicationPayloadHistory> payloads
) {
    public record CommunicationPayloadHistory(
            String contentType,
            String payloadValue,
            Long claimItemId,
            Long attachmentId,
            String attachmentName,
            String attachmentType,
            Long sizeBytes,
            Boolean isSentToWaseel,
            String downloadUrl
    ) {}
}
