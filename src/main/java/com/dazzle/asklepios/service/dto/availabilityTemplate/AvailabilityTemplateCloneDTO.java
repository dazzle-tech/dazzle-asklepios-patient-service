package com.dazzle.asklepios.service.dto.availabilityTemplate;

import com.dazzle.asklepios.domain.enumeration.TemplateCloneType;
import jakarta.validation.constraints.NotNull;

public record AvailabilityTemplateCloneDTO(
        String templateName
) {
}