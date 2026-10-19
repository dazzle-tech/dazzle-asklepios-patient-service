package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.domain.enumeration.DocumentType;

public final class PatientCitizenFlag {

    private PatientCitizenFlag() {
    }

    public static boolean fromDocumentType(DocumentType type) {
        return type == DocumentType.NATIONAL_ID;
    }
}
