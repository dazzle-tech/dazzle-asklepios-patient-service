package com.dazzle.asklepios.web.rest.vm.preauth;

public record DownloadPreAuthorizationAttachmentVM(
        String url,
        int expiresInSeconds
) {}
