
package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.techniciannotes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiagnosticOrderTestTechnicianNoteDTO(
        @NotNull Long orderTestId,
        @NotNull Long orderId,
        @NotBlank String note
) {
}
