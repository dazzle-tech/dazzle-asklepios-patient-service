package com.dazzle.asklepios.service.dto.patientEncounter;

import java.io.Serializable;
import java.util.List;

public record DiagnosticCheckoutApproval(
        Long orderTestId,
        Long testId,
        String testName,
        String orderType,
        String orderNumber,
        Boolean urgent,
        String testStatus,
        String processingStatus,
        String approvalStatus,
        String imageStatus,
        String accessionNumber,
        String message,
        List<DiagnosticCheckoutResultLine> results
) implements Serializable {
}
