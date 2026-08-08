package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.dazzle.asklepios.domain.enumeration.DocumentType;

public record CchiPatientDocumentOption(
        Long id,
        String documentId,
        DocumentType type,
        Boolean isPrimary,
        Long countryId
) {}
