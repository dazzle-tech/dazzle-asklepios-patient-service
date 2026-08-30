package com.dazzle.asklepios.web.rest.vm.documentVersion;

import com.dazzle.asklepios.domain.enumeration.DocumentStatus;

import java.time.Instant;

public record DocumentVersionResponseVM(

        Long id,

        Long documentDefinitionId,

        Integer version,

        String fileName,

        String mimeType,

        DocumentStatus status,

        Instant createdDate,
        Instant effectiveFromDate,
        Instant effectiveToDate
) {
}
