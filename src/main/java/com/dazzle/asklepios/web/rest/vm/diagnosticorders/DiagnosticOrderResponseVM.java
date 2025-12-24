package com.dazzle.asklepios.web.rest.vm.diagnosticorders;

import com.dazzle.asklepios.domain.DiagnosticOrder;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderResponseVM(
        Long id,
        Long patientId,
        Long encounterId,
        String status,
        Long orderNumber,
        Boolean saveDraft,
        String submittedBy,
        Instant submittedDate,
        Boolean isUrgent,
        String labStatus,
        String radStatus
) implements Serializable {

    public static DiagnosticOrderResponseVM ofEntity(DiagnosticOrder o) {
        return new DiagnosticOrderResponseVM(
                o.getId(),
                o.getPatientId(),
                o.getEncounterId(),
                o.getStatus(),
                o.getOrderNumber(),
                o.getSaveDraft(),
                o.getSubmittedBy(),
                o.getSubmittedDate(),
                o.getIsUrgent(),
                o.getLabStatus(),
                o.getRadStatus()
        );
    }
}
