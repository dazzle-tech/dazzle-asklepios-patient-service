package com.dazzle.asklepios.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pre_authorization_attachments")
public class PreAuthorizationAttachment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pre_authorization_id", nullable = false)
    private Long preAuthorizationId;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "space_key", nullable = false, length = 500)
    private String spaceKey;

    @Column(name = "filename", nullable = false, columnDefinition = "TEXT")
    private String filename;

    @Column(name = "mime_type", nullable = false, columnDefinition = "TEXT")
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "type", length = 100)
    private String type;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "supporting_info_id")
    private Long supportingInfoId;

    @Column(name = "is_sent_to_waseel", nullable = false)
    private Boolean isSentToWaseel = false;

    @Column(name = "waseel_attachment_id", length = 100)
    private String waseelAttachmentId;

    @Column(name = "last_modified_by", length = 50)
    private String lastModifiedBy;

    @Column(name = "last_modified_date")
    private Instant lastModifiedDate;
}