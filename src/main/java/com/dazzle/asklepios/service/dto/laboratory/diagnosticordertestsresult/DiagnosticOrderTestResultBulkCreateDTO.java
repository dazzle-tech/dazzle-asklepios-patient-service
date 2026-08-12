package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serializable;
import java.util.List;

public record DiagnosticOrderTestResultBulkCreateDTO(

        @NotEmpty
        List<@Valid DiagnosticOrderTestResultCreateDTO> results

) implements Serializable {
}