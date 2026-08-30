package com.dazzle.asklepios.web.rest.vm.documentAssignment;


import com.dazzle.asklepios.domain.enumeration.DocumentRequirementStatus;

public record DocumentRequirementVM(
        Long assignmentId,
        Long documentId,
        String documentCode,
        String documentName,
        Long documentVersionId,
        Integer version,
        Boolean required,
        Boolean blocking,
        DocumentRequirementStatus status
) {
}
