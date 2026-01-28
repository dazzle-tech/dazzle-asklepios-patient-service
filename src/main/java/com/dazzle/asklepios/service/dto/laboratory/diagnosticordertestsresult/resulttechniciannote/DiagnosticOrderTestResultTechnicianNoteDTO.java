
package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.resulttechniciannote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiagnosticOrderTestResultTechnicianNoteDTO(
        @NotNull Long orderTestId,
        @NotNull Long orderId,
        @NotNull Long resultId,
        @NotBlank String note
) {
}
