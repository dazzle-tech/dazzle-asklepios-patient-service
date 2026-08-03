package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

import java.util.List;

/**
 * Waseel communication request.
 * <p>
 * Prefer sending {@code preAuthorizationId}. When present, the backend replaces
 * each payload {@code claimItemId} with the stored Waseel {@code waseelItemId}
 * from search (never reuse item sequence as claimItemId).
 * <p>
 * Each payload may include plain text ({@code payloadValue}) and/or an attachment.
 * Prefer uploading via the pre-authorization attachments API first and passing
 * {@code attachmentId}. Direct base64 via {@code payloadAttachment} is also supported
 * and will be persisted to {@code pre_authorization_attachments}.
 */
public record PreAuthorizationCommunicationRequest(
        Long preAuthorizationId,
        Long claimResponseId,
        List<Payload> payloads
) {
    public record Payload(
            String attachmentName,
            String attachmentType,
            Long claimItemId,
            String createdDate,
            String payloadAttachment,
            String payloadValue,
            Long attachmentId
    ) {}
}