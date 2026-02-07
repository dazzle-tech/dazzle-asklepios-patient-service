package com.dazzle.asklepios.web.rest.vm.radiology;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportImageStatusLog;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;

import java.time.Instant;

/**
 * Response VM for report image status log rows.
 */
public record DiagnosticOrderTestReportImageStatusLogResponseVM(
        Long id,
        Long reportId,
        Instant statusDate,
        String statusBy,
        RadiologyImageStatus statusValue
) {
    public static DiagnosticOrderTestReportImageStatusLogResponseVM ofEntity(DiagnosticOrderTestReportImageStatusLog e) {
        return new DiagnosticOrderTestReportImageStatusLogResponseVM(
                e.getId(),
                e.getReportId(),
                e.getStatusDate(),
                e.getStatusBy(),
                e.getStatusValue()
        );
    }
}
