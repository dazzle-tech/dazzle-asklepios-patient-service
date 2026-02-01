package com.dazzle.asklepios.web.rest.vm.laboratory;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResultTechnicianNote;

import java.time.Instant;

public record DiagnosticOrderTestResultTechnicianNoteResponseVM(
        Long id,
        Long orderTestId,
        String note,
        String createdBy,
        Instant createdDate
) {
    public static DiagnosticOrderTestResultTechnicianNoteResponseVM ofEntity(DiagnosticOrderTestResultTechnicianNote e) {
        return new DiagnosticOrderTestResultTechnicianNoteResponseVM(
                e.getId(),
                e.getOrderTestId(),
                e.getNote(),
                e.getCreatedBy(),
                e.getCreatedDate()
        );
    }
}