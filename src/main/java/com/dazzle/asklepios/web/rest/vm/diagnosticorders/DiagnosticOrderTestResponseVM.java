package com.dazzle.asklepios.web.rest.vm.diagnosticorders;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestResponseVM(
        Long id,
        Long patientId,
        Long encounterId,

        DiagnosticOrderTestStatus status,      // lifecycle: NEW/SUBMITTED/CANCELLED
        DiagnosticStatus processingStatus,     // workflow: SAMPLE_COLLECTED/ACCEPTED/...

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

        Long fromDepartmentId,
        Long fromFacilityId,
        Long toFacilityId
) implements Serializable {

    public static DiagnosticOrderTestResponseVM ofEntity(DiagnosticOrderTest t) {
        return new DiagnosticOrderTestResponseVM(
                t.getId(),
                t.getPatientId(),
                t.getEncounterId(),

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

                t.getFromDepartmentId(),
                t.getFromFacilityId(),
                t.getToFacilityId()
        );
    }
}
