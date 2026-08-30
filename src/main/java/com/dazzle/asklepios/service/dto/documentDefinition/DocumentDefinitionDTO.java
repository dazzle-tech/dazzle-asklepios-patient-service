package com.dazzle.asklepios.service.dto.documentDefinition;

import com.dazzle.asklepios.domain.enumeration.DocumentCategory;
import com.dazzle.asklepios.domain.enumeration.DocumentStatus;
import jakarta.validation.constraints.NotBlank;

public record DocumentDefinitionDTO(

        @NotBlank
        String code,

        @NotBlank
        String name,

        String description,

        DocumentCategory category,

        DocumentStatus status
) {
}