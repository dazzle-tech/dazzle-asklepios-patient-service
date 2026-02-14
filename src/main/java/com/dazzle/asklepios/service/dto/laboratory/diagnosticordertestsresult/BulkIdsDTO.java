package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;

import jakarta.validation.constraints.NotEmpty;

import java.io.Serializable;
import java.util.List;

public record BulkIdsDTO(
        @NotEmpty List<Long> ids
) implements Serializable {}
