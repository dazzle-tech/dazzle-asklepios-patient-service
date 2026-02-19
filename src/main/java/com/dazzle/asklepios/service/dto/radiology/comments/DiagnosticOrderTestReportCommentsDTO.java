
package com.dazzle.asklepios.service.dto.radiology.comments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiagnosticOrderTestReportCommentsDTO(
        @NotNull Long reportId,
        @NotNull Long orderTestId,
        @NotBlank String note
) {
}
