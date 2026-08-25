package com.dazzle.asklepios.service.dto.documentAssignment;

import com.dazzle.asklepios.domain.enumeration.DocumentTargetType;
import com.dazzle.asklepios.domain.enumeration.DocumentTriggerType;
import jakarta.validation.constraints.NotNull;

public record DocumentAssignmentDTO(

        Long documentVersionId,

        @NotNull
        DocumentTargetType targetType,

        Long targetId,

        DocumentTriggerType triggerType,

        boolean required,

        boolean blocking,

        Boolean active
) {
}
