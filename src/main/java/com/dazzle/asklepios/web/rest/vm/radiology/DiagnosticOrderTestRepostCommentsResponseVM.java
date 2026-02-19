package com.dazzle.asklepios.web.rest.vm.radiology;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportComments;

import java.time.Instant;

public record DiagnosticOrderTestRepostCommentsResponseVM(
        Long id,
        Long reportId,
        Long orderId,
        String note,
        String createdBy,
        Instant createdDate
) {
    public static DiagnosticOrderTestRepostCommentsResponseVM ofEntity(DiagnosticOrderTestReportComments e) {
        return new DiagnosticOrderTestRepostCommentsResponseVM(
                e.getId(),
                e.getReportId(),
                e.getOrderTestId(),
                e.getNote(),
                e.getCreatedBy(),
                e.getCreatedDate()
        );
    }
}