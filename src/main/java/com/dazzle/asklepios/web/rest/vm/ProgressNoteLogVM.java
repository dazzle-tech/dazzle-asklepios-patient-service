package com.dazzle.asklepios.web.rest.vm;

import java.time.Instant;

public record ProgressNoteLogVM(
        Long id,
        String action,
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate,
        String payload
) {
}
