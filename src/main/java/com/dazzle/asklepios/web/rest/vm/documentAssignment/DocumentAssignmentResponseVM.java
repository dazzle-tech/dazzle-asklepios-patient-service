package com.dazzle.asklepios.web.rest.vm.documentAssignment;

import com.dazzle.asklepios.domain.enumeration.DocumentTargetType;
import com.dazzle.asklepios.domain.enumeration.DocumentTriggerType;
import com.dazzle.asklepios.web.rest.vm.documentVersion.DocumentVersionResponseVM;

public record DocumentAssignmentResponseVM(
        Long id,
        Long documentId,
        String documentName,
        String documentCode,
        DocumentVersionResponseVM documentVersion,
        DocumentTargetType triggerType,
        Long targetId,
        DocumentTriggerType trigger,
        Boolean required,
        Boolean blocking,
        Boolean active
) {
}
