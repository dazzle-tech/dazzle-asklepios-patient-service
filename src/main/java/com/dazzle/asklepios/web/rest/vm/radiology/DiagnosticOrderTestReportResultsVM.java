package com.dazzle.asklepios.web.rest.vm.radiology;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestReportResultsVM(
        Long id,
        Long orderTestId,

        Long testId,

        String patientName,
        String mrn,

        Long encounterId,

        String orderedBy,
        Instant orderedAt,

        String report,
        String radiologistInformation,
        String criticalFindings,
        String radiologistComments,
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

    public static DiagnosticOrderTestReportResultsVM ofEntity(
            DiagnosticOrderTestReport entity,
            Long testId,
            String patientName,
            String mrn,
            Long encounterId,
            String orderedBy,
            Instant orderedAt,
            boolean hasNote
    ) {
        return new DiagnosticOrderTestReportResultsVM(
                entity.getId(),
                entity.getOrderTestId(),

                testId,

                patientName,
                mrn,

                encounterId,

                orderedBy,
                orderedAt,

                entity.getReport(),
                entity.getRadiologistInformation(),
                entity.getCriticalFindings(),
                entity.getRadiologistComments(),
                entity.getSeverity(),

                entity.getApprovedBy(),
                entity.getApprovedDate(),

                entity.getRejectedBy(),
                entity.getRejectedDate(),
                entity.getRejectedReason(),

                entity.getReviewBy(),
                entity.getReviewDate(),

                entity.getSecondApprovedBy(),
                entity.getSecondApprovedDate(),

                entity.getProcessingStatus(),
                entity.getImageStatus(),

                entity.getCreatedDate(),
                entity.getCreatedBy(),
                entity.getLastModifiedDate(),
                entity.getLastModifiedBy(),

                entity.getAccessionNumber(),
                hasNote
        );
    }
}