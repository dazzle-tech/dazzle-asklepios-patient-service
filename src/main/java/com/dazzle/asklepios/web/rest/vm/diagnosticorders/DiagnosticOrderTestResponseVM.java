package com.dazzle.asklepios.web.rest.vm.diagnosticorders;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestResponseVM(
        Long id,

        DiagnosticOrderTestStatus status,
        DiagnosticStatus processingStatus,    

        Long orderId,
        Long testId,
        TestType orderType,

        Long receivedDepartmentId,
        String reason,
        String notes,

        Instant submitDate,
        Instant acceptedDate,
        Instant rejectedDate,
        Instant patientArrivedDate,
        Instant readyDate,
        Instant approvedDate,

        String acceptedBy,
        String rejectedBy,
        String rejectedReason,
        String patientArrivedNoteRad,

        String cancellationReason,
        String cancelledBy,
        Instant cancelledDate,
        Instant createdDate,
        Instant lastModifiedDate,
        String createdBy,
        String lastModifiedBy
) implements Serializable {

    public static DiagnosticOrderTestResponseVM ofEntity(DiagnosticOrderTest t) {
        return new DiagnosticOrderTestResponseVM(
                t.getId(),

                t.getStatus(),
                t.getProcessingStatus(),

                t.getOrderId(),
                t.getTestId(),
                t.getOrderType(),

                t.getReceivedDepartmentId(),
                t.getReason(),
                t.getNotes(),

                t.getSubmitDate(),
                t.getAcceptedDate(),
                t.getRejectedDate(),
                t.getPatientArrivedDate(),
                t.getReadyDate(),
                t.getApprovedDate(),

                t.getAcceptedBy(),
                t.getRejectedBy(),
                t.getRejectedReason(),
                t.getPatientArrivedNoteRad(),

                t.getCancellationReason(),
                t.getCancelledBy(),
                t.getCancelledDate(),

                t.getCreatedDate(),
                t.getLastModifiedDate(),
                t.getCreatedBy(),
                t.getLastModifiedBy()
        );
    }
}