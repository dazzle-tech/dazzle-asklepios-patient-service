package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serializable;
import java.util.List;

public record BulkRejectDTO(
        @NotEmpty List<Long> ids,
        @NotBlank String rejectedReason
) implements Serializable {}
