package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record DiagnosticOrderTestResultRejectDTO(
        @NotBlank String rejectedReason
) implements Serializable {}