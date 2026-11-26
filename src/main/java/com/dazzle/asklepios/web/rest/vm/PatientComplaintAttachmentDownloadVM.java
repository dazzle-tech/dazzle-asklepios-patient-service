package com.dazzle.asklepios.web.rest.vm;

/**
 * Presigned download ticket — same shape as DocumentTemplateDownloadVM /
 * patient attachment DownloadTicket (url + expiresInSeconds).
 */
public record PatientComplaintAttachmentDownloadVM(String url, int expiresInSeconds) {}
