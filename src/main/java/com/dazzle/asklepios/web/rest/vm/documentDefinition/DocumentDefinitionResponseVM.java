package com.dazzle.asklepios.web.rest.vm.documentDefinition;

import com.dazzle.asklepios.domain.enumeration.DocumentCategory;
import com.dazzle.asklepios.domain.enumeration.DocumentStatus;
import com.dazzle.asklepios.web.rest.vm.documentVersion.DocumentVersionResponseVM;

public record DocumentDefinitionResponseVM(

        Long id,

        String code,

        String name,

        String description,

        DocumentCategory category,

        DocumentStatus status,

        DocumentVersionResponseVM activeVersion
) {
}
