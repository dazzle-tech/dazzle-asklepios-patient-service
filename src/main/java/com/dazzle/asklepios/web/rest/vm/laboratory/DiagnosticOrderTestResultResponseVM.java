package com.dazzle.asklepios.web.rest.vm.laboratory;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosticOrderTestResultResponseVM(
        Long id,
        Long orderId,
        Long orderTestId,
        Long profileTestId,
        BigDecimal resultValueNumber,
        String resultValueText,
        TestResultMarker marker,
        String approvedBy,
        Instant approvedDate,
        String rejectedBy,
        Instant rejectedDate,
        String rejectedReason,
        String reviewBy,
        Instant reviewDate,
        DiagnosticStatus processingStatus,
        String normalRangeValue,
        Instant createdDate,
        Instant lastModifiedDate,
        String createdBy,
        String lastModifiedBy
) implements Serializable {

    public static DiagnosticOrderTestResultResponseVM ofEntity(DiagnosticOrderTestResult r) {
        return new DiagnosticOrderTestResultResponseVM(
                r.getId(),
                r.getOrderId(),
                r.getOrderTestId(),
                r.getProfileTestId(),
                r.getResultValueNumber(),
                r.getResultValueText(),
                r.getMarker(),
                r.getApprovedBy(),
                r.getApprovedDate(),
                r.getRejectedBy(),
                r.getRejectedDate(),
                r.getRejectedReason(),
                r.getReviewBy(),
                r.getReviewDate(),
                r.getProcessingStatus(),
                r.getNormalRangeValue(),
                r.getCreatedDate(),
                r.getLastModifiedDate(),
                r.getCreatedBy(),
                r.getLastModifiedBy()
        );
    }
}
