package com.dazzle.asklepios.web.rest.vm.radiology;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestReportResponseVM(
        Long id,
        Long orderTestId,
        String report,
        Severity severity,
        String approvedBy,
        Instant approvedDate,
        String rejectedBy,
        Instant rejectedDate,
        String rejectedReason,
        String reviewBy,
        Instant reviewDate,
        String secondApprovedBy,
        Instant secondApprovedDate,
        DiagnosticStatus processingStatus,
        RadiologyImageStatus imageStatus,
        Instant createdDate,
        String createdBy,
        Instant lastModifiedDate,
        String lastModifiedBy,
        String accessionNumber,
        boolean hasNote
) implements Serializable {

    public static DiagnosticOrderTestReportResponseVM ofEntity(DiagnosticOrderTestReport diagnosticOrderTestReport) {
        return new DiagnosticOrderTestReportResponseVM(
                diagnosticOrderTestReport.getId(),
                diagnosticOrderTestReport.getOrderTestId(),
                diagnosticOrderTestReport.getReport(),
                diagnosticOrderTestReport.getSeverity(),
                diagnosticOrderTestReport.getApprovedBy(),
                diagnosticOrderTestReport.getApprovedDate(),
                diagnosticOrderTestReport.getRejectedBy(),
                diagnosticOrderTestReport.getRejectedDate(),
                diagnosticOrderTestReport.getRejectedReason(),
                diagnosticOrderTestReport.getReviewBy(),
                diagnosticOrderTestReport.getReviewDate(),
                diagnosticOrderTestReport.getSecondApprovedBy(),
                diagnosticOrderTestReport.getSecondApprovedDate(),
                diagnosticOrderTestReport.getProcessingStatus(),
                diagnosticOrderTestReport.getImageStatus(),
                diagnosticOrderTestReport.getCreatedDate(),
                diagnosticOrderTestReport.getCreatedBy(),
                diagnosticOrderTestReport.getLastModifiedDate(),
                diagnosticOrderTestReport.getLastModifiedBy(),
                diagnosticOrderTestReport.getAccessionNumber(),
                false
        );
    }

    public static DiagnosticOrderTestReportResponseVM ofEntityWithNote(DiagnosticOrderTestReport diagnosticOrderTestReport, boolean hasNote) {
        return new DiagnosticOrderTestReportResponseVM(
                diagnosticOrderTestReport.getId(),
                diagnosticOrderTestReport.getOrderTestId(),
                diagnosticOrderTestReport.getReport(),
                diagnosticOrderTestReport.getSeverity(),
                diagnosticOrderTestReport.getApprovedBy(),
                diagnosticOrderTestReport.getApprovedDate(),
                diagnosticOrderTestReport.getRejectedBy(),
                diagnosticOrderTestReport.getRejectedDate(),
                diagnosticOrderTestReport.getRejectedReason(),
                diagnosticOrderTestReport.getReviewBy(),
                diagnosticOrderTestReport.getReviewDate(),
                diagnosticOrderTestReport.getSecondApprovedBy(),
                diagnosticOrderTestReport.getSecondApprovedDate(),
                diagnosticOrderTestReport.getProcessingStatus(),
                diagnosticOrderTestReport.getImageStatus(),
                diagnosticOrderTestReport.getCreatedDate(),
                diagnosticOrderTestReport.getCreatedBy(),
                diagnosticOrderTestReport.getLastModifiedDate(),
                diagnosticOrderTestReport.getLastModifiedBy(),
                diagnosticOrderTestReport.getAccessionNumber(),
                hasNote

        );
    }
}
