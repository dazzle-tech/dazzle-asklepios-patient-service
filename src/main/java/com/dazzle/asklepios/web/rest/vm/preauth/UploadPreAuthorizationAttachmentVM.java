package com.dazzle.asklepios.web.rest.vm.preauth;

import org.springframework.web.multipart.MultipartFile;

public record UploadPreAuthorizationAttachmentVM(
        MultipartFile file,
        String type,
        String details,
        String source,
        Long sourceId
) {}
