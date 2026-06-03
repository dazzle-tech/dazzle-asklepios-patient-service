package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

import java.util.List;

public record PreAuthorizationCommunicationRequest(
        Long claimResponseId,
        List<Payload> payloads
) {
    public record Payload(
            String attachmentName,
            String attachmentType,
            Long claimItemId,
            String createdDate,
            String payloadAttachment,
            String payloadValue
    ) {}
}