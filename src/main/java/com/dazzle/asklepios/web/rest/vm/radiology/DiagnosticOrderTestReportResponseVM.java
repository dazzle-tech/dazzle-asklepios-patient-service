// src/main/java/com/dazzle/asklepios/web/rest/vm/radiology/DiagnosticOrderTestReportResponseVM.java
package com.dazzle.asklepios.web.rest.vm.radiology;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestReportResponseVM(
        Long id,
        Long orderId,
        Long orderTestId,
        String report,
        String severity,
        String approvedBy,
        Instant approvedDate,
        String rejectedBy,
        Instant rejectedDate,
        String rejectedReason,
        String reviewBy,
        Instant reviewDate,
        DiagnosticStatus processingStatus,
        RadiologyImageStatus imageStatus,
        Instant createdDate,
        String createdBy,
        Instant lastModifiedDate,
        String lastModifiedBy
) implements Serializable {

    public static DiagnosticOrderTestReportResponseVM ofEntity(DiagnosticOrderTestReport e) {
        return new DiagnosticOrderTestReportResponseVM(
                e.getId(),
                e.getOrderId(),
                e.getOrderTestId(),
                e.getReport(),
                e.getSeverity(),
                e.getApprovedBy(),
                e.getApprovedDate(),
                e.getRejectedBy(),
                e.getRejectedDate(),
                e.getRejectedReason(),
                e.getReviewBy(),
                e.getReviewDate(),
                e.getProcessingStatus(),
                e.getImageStatus(),
                e.getCreatedDate(),
                e.getCreatedBy(),
                e.getLastModifiedDate(),
                e.getLastModifiedBy()
        );
    }
}
