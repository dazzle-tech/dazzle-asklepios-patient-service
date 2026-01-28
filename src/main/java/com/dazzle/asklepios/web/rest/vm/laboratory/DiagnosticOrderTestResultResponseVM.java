package com.dazzle.asklepios.web.rest.vm.laboratory;

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

        // persisted fields (DB)
        TestResultMarker marker,

        // view-only fields (NOT persisted)
        TestResultMarker viewMarker,
        String viewNormalRange,

        // view-only: indicates if this result has any log/note entries (NOT persisted)
        Boolean hasNote,

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

    public static DiagnosticOrderTestResultResponseVM ofEntity(DiagnosticOrderTestResult r) {
        return new DiagnosticOrderTestResultResponseVM(
                r.getId(),
                r.getOrderId(),
                r.getOrderTestId(),
                r.getProfileTestId(),
                r.getResultValueNumber(),
                r.getResultValueText(),
                r.getMarker(),

                // view-only defaults = persisted values
                r.getMarker(),
                r.getNormalRangeValue(),

                // default (no extra lookup here)
                false,

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

    public static DiagnosticOrderTestResultResponseVM ofEntityWithView(
            DiagnosticOrderTestResult r,
            TestResultMarker viewMarker,
            String viewNormalRange
    ) {
        return ofEntityWithViewAndNote(r, viewMarker, viewNormalRange, false);
    }

    public static DiagnosticOrderTestResultResponseVM ofEntityWithViewAndNote(
            DiagnosticOrderTestResult r,
            TestResultMarker viewMarker,
            String viewNormalRange,
            boolean hasNote
    ) {
        return new DiagnosticOrderTestResultResponseVM(
                r.getId(),
                r.getOrderId(),
                r.getOrderTestId(),
                r.getProfileTestId(),
                r.getResultValueNumber(),
                r.getResultValueText(),
                r.getMarker(),

                viewMarker,
                viewNormalRange,

                hasNote,

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
