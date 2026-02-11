package com.dazzle.asklepios.web.rest.vm.laboratory;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosticOrderTestResultResponseVM(
        Long id,
        Long orderTestId,
        Long profileTestId,
        BigDecimal resultValueNumber,
        String resultValueText,

        // persisted fields (DB)
        TestResultMarker marker,

        // view-only fields (NOT persisted)
        TestResultMarker viewMarker,
        String viewNormalRange,

        String approvedBy,
        Instant approvedDate,
        String rejectedBy,
        Instant rejectedDate,
        String rejectedReason,
        String reviewBy,
        Instant reviewDate,
        DiagnosticStatus processingStatus,

        // persisted
        String normalRangeValue,

        Instant createdDate,
        Instant lastModifiedDate,
        String createdBy,
        String lastModifiedBy
) implements Serializable {

    public static DiagnosticOrderTestResultResponseVM ofEntity(DiagnosticOrderTestResult diagnosticOrderTestResult) {
        return new DiagnosticOrderTestResultResponseVM(
                diagnosticOrderTestResult.getId(),
                diagnosticOrderTestResult.getOrderTestId(),
                diagnosticOrderTestResult.getProfileTestId(),
                diagnosticOrderTestResult.getResultValueNumber(),
                diagnosticOrderTestResult.getResultValueText(),
                diagnosticOrderTestResult.getMarker(),

                // view-only defaults = persisted values
                diagnosticOrderTestResult.getMarker(),
                diagnosticOrderTestResult.getNormalRangeValue(),

                diagnosticOrderTestResult.getApprovedBy(),
                diagnosticOrderTestResult.getApprovedDate(),
                diagnosticOrderTestResult.getRejectedBy(),
                diagnosticOrderTestResult.getRejectedDate(),
                diagnosticOrderTestResult.getRejectedReason(),
                diagnosticOrderTestResult.getReviewBy(),
                diagnosticOrderTestResult.getReviewDate(),
                diagnosticOrderTestResult.getProcessingStatus(),
                diagnosticOrderTestResult.getNormalRangeValue(),
                diagnosticOrderTestResult.getCreatedDate(),
                diagnosticOrderTestResult.getLastModifiedDate(),
                diagnosticOrderTestResult.getCreatedBy(),
                diagnosticOrderTestResult.getLastModifiedBy()
        );
    }

    public static DiagnosticOrderTestResultResponseVM ofEntityWithView(
            DiagnosticOrderTestResult diagnosticOrderTestResult,
            TestResultMarker viewMarker,
            String viewNormalRange
    ) {
        return new DiagnosticOrderTestResultResponseVM(
                diagnosticOrderTestResult.getId(),
                diagnosticOrderTestResult.getOrderTestId(),
                diagnosticOrderTestResult.getProfileTestId(),
                diagnosticOrderTestResult.getResultValueNumber(),
                diagnosticOrderTestResult.getResultValueText(),
                diagnosticOrderTestResult.getMarker(),

                viewMarker,
                viewNormalRange,

                diagnosticOrderTestResult.getApprovedBy(),
                diagnosticOrderTestResult.getApprovedDate(),
                diagnosticOrderTestResult.getRejectedBy(),
                diagnosticOrderTestResult.getRejectedDate(),
                diagnosticOrderTestResult.getRejectedReason(),
                diagnosticOrderTestResult.getReviewBy(),
                diagnosticOrderTestResult.getReviewDate(),
                diagnosticOrderTestResult.getProcessingStatus(),
                diagnosticOrderTestResult.getNormalRangeValue(),
                diagnosticOrderTestResult.getCreatedDate(),
                diagnosticOrderTestResult.getLastModifiedDate(),
                diagnosticOrderTestResult.getCreatedBy(),
                diagnosticOrderTestResult.getLastModifiedBy()
        );
    }

}
