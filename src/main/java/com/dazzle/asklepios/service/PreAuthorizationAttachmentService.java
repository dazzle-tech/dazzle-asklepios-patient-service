package com.dazzle.asklepios.service;

import com.dazzle.asklepios.attachments.AttachmentProperties;
import com.dazzle.asklepios.config.Constants;
import com.dazzle.asklepios.domain.PreAuthorizationAttachment;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.repository.PreAuthorizationAttachmentRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.preauth.DownloadPreAuthorizationAttachmentVM;
import com.dazzle.asklepios.web.rest.vm.preauth.PreAuthorizationAttachmentResponse;
import com.dazzle.asklepios.web.rest.vm.preauth.UploadPreAuthorizationAttachmentVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationAttachmentService {

    public static final String SOURCE_COMMUNICATION = "COMMUNICATION";
    private static final String ENTITY_NAME = "PreAuthorizationAttachment";

    private static final DateTimeFormatter YYYY =
            DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MM =
            DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);

    private final PreAuthorizationAttachmentRepository repository;
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final AttachmentProperties props;
    private final AttachmentStorageService storage;

    @Transactional
    public PreAuthorizationAttachmentResponse upload(
            Long preAuthorizationId,
            UploadPreAuthorizationAttachmentVM upload
    ) {
        PreAuthorizationRequest preAuth = requirePreAuthorization(preAuthorizationId);
        MultipartFile file = upload.file();
        if (file == null || file.isEmpty()) {
            throw new BadRequestAlertException("No file provided", ENTITY_NAME, "no_file");
        }

        Instant now = Instant.now();
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        long size = file.getSize();
        validateFile(mime, size);

        String originalName = getOriginalName(file);
        String key = buildSpaceKey(preAuthorizationId, now, originalName);

        try {
            storage.put(key, mime, size, file.getInputStream());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed", ex);
        }

        PreAuthorizationAttachment saved = repository.save(
                buildEntity(
                        preAuth.getId(),
                        key,
                        originalName,
                        mime,
                        size,
                        blankToNull(upload.type()),
                        blankToNull(upload.details()),
                        blankToNull(upload.source()) != null ? upload.source().trim() : SOURCE_COMMUNICATION,
                        upload.sourceId() != null ? upload.sourceId() : preAuth.getId(),
                        now
                )
        );

        return toResponse(saved);
    }

    @Transactional
    public PreAuthorizationAttachment saveFromBase64(
            Long preAuthorizationId,
            String filename,
            String mimeType,
            String base64Content,
            String details,
            String source,
            Long sourceId
    ) {
        PreAuthorizationRequest preAuth = requirePreAuthorization(preAuthorizationId);

        String mime = blankToNull(mimeType) != null ? mimeType.trim() : "application/octet-stream";
        String name = blankToNull(filename) != null ? sanitizeFilename(filename) : "attachment";
        String base64 = blankToNull(base64Content);
        if (base64 == null) {
            throw new BadRequestAlertException("No attachment content provided", ENTITY_NAME, "no_file");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException("Invalid base64 attachment", ENTITY_NAME, "invalid_base64");
        }

        long size = bytes.length;
        validateFile(mime, size);

        Instant now = Instant.now();
        String key = buildSpaceKey(preAuthorizationId, now, name);
        try {
            storage.putBytes(key, mime, bytes);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed", ex);
        }

        return repository.save(
                buildEntity(
                        preAuth.getId(),
                        key,
                        name,
                        mime,
                        size,
                        SOURCE_COMMUNICATION,
                        blankToNull(details),
                        blankToNull(source) != null ? source.trim() : SOURCE_COMMUNICATION,
                        sourceId != null ? sourceId : preAuth.getId(),
                        now
                )
        );
    }

    public List<PreAuthorizationAttachmentResponse> list(Long preAuthorizationId) {
        requirePreAuthorization(preAuthorizationId);
        return repository
                .findByPreAuthorizationIdAndDeletedAtIsNullOrderByCreatedDateDesc(preAuthorizationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DownloadPreAuthorizationAttachmentVM downloadUrl(Long id) {
        PreAuthorizationAttachment attachment = requireAttachment(id);
        // Private Spaces objects are not publicly readable via CDN.
        // Clients must use the authenticated /file endpoint.
        return new DownloadPreAuthorizationAttachmentVM(
                "/api/patient/internal/waseel/pre-authorizations/attachments/"
                        + attachment.getId()
                        + "/file",
                props.getPresignExpirySeconds()
        );
    }

    public PreAuthorizationAttachment requireAttachment(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization attachment not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    public ResponseEntity<byte[]> downloadFile(Long id) {
        PreAuthorizationAttachment attachment = requireAttachment(id);
        byte[] bytes = storage.getBytes(attachment.getSpaceKey());

        String mime = blankToNull(attachment.getMimeType()) != null
                ? attachment.getMimeType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String filename = blankToNull(attachment.getFilename()) != null
                ? attachment.getFilename()
                : "attachment";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename.replace("\"", "") + "\""
                )
                .contentType(MediaType.parseMediaType(mime))
                .contentLength(bytes.length)
                .body(bytes);
    }

    public PreAuthorizationAttachment requireOwnedAttachment(Long attachmentId, Long preAuthorizationId) {
        return repository
                .findByIdAndPreAuthorizationIdAndDeletedAtIsNull(attachmentId, preAuthorizationId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization attachment not found with id " + attachmentId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    public byte[] loadBytes(PreAuthorizationAttachment attachment) {
        return storage.getBytes(attachment.getSpaceKey());
    }

    @Transactional
    public void markSentToWaseel(Long attachmentId, String waseelAttachmentId) {
        PreAuthorizationAttachment attachment = repository.findByIdAndDeletedAtIsNull(attachmentId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization attachment not found with id " + attachmentId,
                        ENTITY_NAME,
                        "notfound"
                ));
        attachment.setIsSentToWaseel(Boolean.TRUE);
        if (blankToNull(waseelAttachmentId) != null) {
            attachment.setWaseelAttachmentId(waseelAttachmentId);
        }
        attachment.setLastModifiedBy(currentUser());
        attachment.setLastModifiedDate(Instant.now());
        repository.save(attachment);
    }

    @Transactional
    public void softDelete(Long id) {
        PreAuthorizationAttachment attachment = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization attachment not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
        if (attachment.getDeletedAt() == null) {
            attachment.setDeletedAt(Instant.now());
            attachment.setLastModifiedBy(currentUser());
            attachment.setLastModifiedDate(Instant.now());
            repository.save(attachment);
        }
    }

    private PreAuthorizationAttachment buildEntity(
            Long preAuthorizationId,
            String spaceKey,
            String filename,
            String mimeType,
            long sizeBytes,
            String type,
            String details,
            String source,
            Long sourceId,
            Instant now
    ) {
        String user = currentUser();
        return PreAuthorizationAttachment.builder()
                .preAuthorizationId(preAuthorizationId)
                .createdBy(user)
                .spaceKey(spaceKey)
                .filename(filename)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .createdDate(now)
                .type(type)
                .details(details)
                .source(source)
                .sourceId(sourceId)
                .isSentToWaseel(Boolean.FALSE)
                .lastModifiedBy(user)
                .lastModifiedDate(now)
                .build();
    }

    private PreAuthorizationAttachmentResponse toResponse(PreAuthorizationAttachment entity) {
        String downloadUrl = "/api/patient/internal/waseel/pre-authorizations/attachments/"
                + entity.getId()
                + "/file";
        return PreAuthorizationAttachmentResponse.of(entity, downloadUrl);
    }

    private PreAuthorizationRequest requirePreAuthorization(Long preAuthorizationId) {
        return preAuthorizationRequestRepository.findById(preAuthorizationId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization not found with id " + preAuthorizationId,
                        "preAuthorization",
                        "notfound"
                ));
    }

    private void validateFile(String mime, long size) {
        if (props.getAllowed() != null && !props.getAllowed().isEmpty() && !props.getAllowed().contains(mime)) {
            throw new BadRequestAlertException("Unsupported file type", ENTITY_NAME, "unsupported_type");
        }
        if (props.getMaxBytes() > 0 && size > props.getMaxBytes()) {
            throw new BadRequestAlertException("File too large", ENTITY_NAME, "too_large");
        }
    }

    private String buildSpaceKey(Long preAuthorizationId, Instant now, String originalName) {
        String safeFileName = UUID.randomUUID() + "_" + sanitizeFilename(originalName);
        return "pre-authorizations/"
                + preAuthorizationId
                + "/"
                + YYYY.format(now)
                + "/"
                + MM.format(now)
                + "/"
                + safeFileName;
    }

    private static String getOriginalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "file";
        }
        return sanitizeFilename(name);
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^\\w.\\- ]", "_");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM);
    }
}
