package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiBeneficiaryData;
import org.springframework.stereotype.Component;

@Component
public class CchiBeneficiaryPatientDocumentMapper {

    private static final Long SAUDI_ARABIA_COUNTRY_ID = 1L;

    public PatientDocument toPatientDocument(CchiBeneficiaryData b) {
        if (b == null || isBlank(b.documentId())) {
            return null;
        }

        return PatientDocument.builder()
                .id(null)
                .patient(null)
                .countryId(SAUDI_ARABIA_COUNTRY_ID)
                .type(mapDocumentType(b.documentType()))
                .number(clean(b.documentId()))
                .isPrimary(Boolean.TRUE)
                .build();
    }

    private DocumentType mapDocumentType(String waseelType) {
        if (isBlank(waseelType)) {
            return DocumentType.NO_DOCUMENT;
        }

        return switch (waseelType.trim().toUpperCase()) {
            case "NI" -> DocumentType.NATIONAL_ID;
            case "PRC" -> DocumentType.IQAMA;
            case "PPN" -> DocumentType.PASSPORT;
            case "MR" -> DocumentType.NO_DOCUMENT;
            case "VP" -> DocumentType.NO_DOCUMENT;
            default -> DocumentType.NO_DOCUMENT;
        };
    }

    private String clean(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null
                || value.trim().isEmpty();
    }
}