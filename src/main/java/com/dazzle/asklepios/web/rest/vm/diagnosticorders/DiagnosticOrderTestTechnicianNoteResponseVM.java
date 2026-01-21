package com.dazzle.asklepios.web.rest.vm.diagnosticorders;

import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;

import java.time.Instant;

public record DiagnosticOrderTestTechnicianNoteResponseVM(
        Long id,
        Long orderTestId,
        Long orderId,
        String note,
        String createdBy,
        Instant createdDate
) {
    public static DiagnosticOrderTestTechnicianNoteResponseVM ofEntity(DiagnosticOrderTestTechnicianNote e) {
        return new DiagnosticOrderTestTechnicianNoteResponseVM(
                e.getId(),
                e.getOrderTestId(),
                e.getOrderId(),
                e.getNote(),
                e.getCreatedBy(),
                e.getCreatedDate()
        );
    }
}