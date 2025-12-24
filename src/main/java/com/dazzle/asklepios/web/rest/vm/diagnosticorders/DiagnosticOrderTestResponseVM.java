// src/main/java/com/dazzle/asklepios/web/rest/vm/diagnosticorders/DiagnosticOrderTestResponseVM.java
package com.dazzle.asklepios.web.rest.vm.diagnosticorders;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestResponseVM(
        Long id,
        Long patientId,
        Long encounterId,
        String status,
        Long orderId,
        Long testId,
        Long receivedDepartmentId,
        String reason,
        String notes,
        String processingStatus,
        Instant submitDate,
        Instant acceptedDate,
        Instant rejectedDate,
        Instant patientArrivedDate,
        Instant readyDate,
        Instant approvedDate,
        String orderType,
        String acceptedBy,
        String rejectedBy,
        String rejectedReason,
        String patientArrivedNoteRad,
        String cancellationReason,
        Long fromDepartmentId,
        Long fromFacilityId,
        Long toFacilityId,
        Boolean isActive
) implements Serializable {

    public static DiagnosticOrderTestResponseVM ofEntity(DiagnosticOrderTest t) {
        return new DiagnosticOrderTestResponseVM(
                t.getId(),
                t.getPatientId(),
                t.getEncounterId(),
                t.getStatus(),
                t.getOrderId(),
                t.getTestId(),
                t.getReceivedDepartmentId(),
                t.getReason(),
                t.getNotes(),
                t.getProcessingStatus(),
                t.getSubmitDate(),
                t.getAcceptedDate(),
                t.getRejectedDate(),
                t.getPatientArrivedDate(),
                t.getReadyDate(),
                t.getApprovedDate(),
                t.getOrderType(),
                t.getAcceptedBy(),
                t.getRejectedBy(),
                t.getRejectedReason(),
                t.getPatientArrivedNoteRad(),
                t.getCancellationReason(),
                t.getFromDepartmentId(),
                t.getFromFacilityId(),
                t.getToFacilityId(),
                t.getIsActive()
        );
    }
}
