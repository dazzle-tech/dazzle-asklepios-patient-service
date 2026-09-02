package com.dazzle.asklepios.service.dto.documentVersion;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

public record DocumentVersionDTO(
        @NotNull MultipartFile file,
        @NotNull Instant effectiveFromDate,
        Instant effectiveToDate

) {
}
