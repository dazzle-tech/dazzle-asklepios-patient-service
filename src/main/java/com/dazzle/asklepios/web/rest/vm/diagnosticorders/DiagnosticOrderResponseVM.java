package com.dazzle.asklepios.web.rest.vm.diagnosticorders;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderResponseVM(
        Long id,
        String patientId,
        Long encounterId,
        DiagnosticStatus status,
        Long orderNumber,
        Boolean saveDraft,
        String submittedBy,
        Instant submittedDate,
        Boolean isUrgent,
        DiagnosticStatus labStatus,
        DiagnosticStatus radStatus,
        Instant createdDate,
        Instant lastModifiedDate,
        String createdBy,
        String lastModifiedBy,
        Long fromDepartmentId,
        Long fromFacilityId
) implements Serializable {

    public static DiagnosticOrderResponseVM ofEntity(DiagnosticOrder o) {
        return new DiagnosticOrderResponseVM(
                o.getId(),
                String.valueOf(o.getPatientId()),
                o.getEncounterId(),
                o.getStatus(),
                o.getOrderNumber(),
                o.getSaveDraft(),
                o.getSubmittedBy(),
                o.getSubmittedDate(),
                o.getIsUrgent(),
                o.getLabStatus(),
                o.getRadStatus(),
                o.getCreatedDate(),
                o.getLastModifiedDate(),
                o.getCreatedBy(),
                o.getLastModifiedBy(),
                o.getFromDepartmentId(),
                o.getFromFacilityId()
        );
    }
}
