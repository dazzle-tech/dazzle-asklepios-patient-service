package com.dazzle.asklepios.web.rest.dto;
import com.dazzle.asklepios.domain.enumeration.PatientServiceCategory;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record PatientServiceProductCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull PatientServiceCategory category,
        Long serviceId,
        Long productId,
        @NotNull Long quantity
) implements Serializable {}