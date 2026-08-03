package com.dazzle.asklepios.web.rest.vm.preauth;

import com.dazzle.asklepios.domain.PreAuthorizationAttachment;

import java.time.Instant;

public record PreAuthorizationAttachmentResponse(
        Long id,
        Long preAuthorizationId,
        String filename,
        String mimeType,
        Long sizeBytes,
        String type,
        String details,
        String source,
        Long sourceId,
        Boolean isSentToWaseel,
        String waseelAttachmentId,
        String spaceKey,
        Instant createdDate,
        String createdBy,
        String downloadUrl
) {
    public static PreAuthorizationAttachmentResponse of(
            PreAuthorizationAttachment entity,
            String downloadUrl
    ) {
        return new PreAuthorizationAttachmentResponse(
                entity.getId(),
                entity.getPreAuthorizationId(),
                entity.getFilename(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                entity.getType(),
                entity.getDetails(),
                entity.getSource(),
                entity.getSourceId(),
                entity.getIsSentToWaseel(),
                entity.getWaseelAttachmentId(),
                entity.getSpaceKey(),
                entity.getCreatedDate(),
                entity.getCreatedBy(),
                downloadUrl
        );
    }
}
