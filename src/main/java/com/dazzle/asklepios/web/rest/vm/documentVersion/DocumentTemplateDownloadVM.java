package com.dazzle.asklepios.web.rest.vm.documentVersion;

public record DocumentTemplateDownloadVM(
        String url,
        int expiresInSeconds
) {
}
