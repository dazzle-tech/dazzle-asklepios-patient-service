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
        String lastModifiedBy,
        boolean hasNote,

        String undoAcceptReason,
        String undoAcceptBy,
        Instant undoAcceptDate,
        Long icdDiagnosisId,

        String confirmedBy,
        Instant confirmedAt,

        String printNumber,

        /** Parent order encounter; populated on filter/list enrichment when available. */
        Long encounterId

) implements Serializable {

    public static DiagnosticOrderTestResponseVM ofEntity(DiagnosticOrderTest orderTest) {
        return ofEntity(orderTest, null);
    }

    public static DiagnosticOrderTestResponseVM ofEntity(
            DiagnosticOrderTest orderTest,
            Long encounterId
    ) {
        return ofEntityWithNote(orderTest, false, encounterId);
    }

    public static DiagnosticOrderTestResponseVM ofEntityWithNote(
            DiagnosticOrderTest orderTest,
            boolean hasNote
    ) {
        return ofEntityWithNote(orderTest, hasNote, null);
    }

    public static DiagnosticOrderTestResponseVM ofEntityWithNote(
            DiagnosticOrderTest orderTest,
            boolean hasNote,
            Long encounterId
    ) {
        return new DiagnosticOrderTestResponseVM(

                orderTest.getId(),

                orderTest.getStatus(),
                orderTest.getProcessingStatus(),

                orderTest.getOrderId(),
                orderTest.getTestId(),
                orderTest.getOrderType(),

                orderTest.getReceivedDepartmentId(),
                orderTest.getReason(),
                orderTest.getNotes(),

                orderTest.getSubmitDate(),
                orderTest.getAcceptedDate(),
                orderTest.getRejectedDate(),
                orderTest.getPatientArrivedDate(),
                orderTest.getReadyDate(),
                orderTest.getApprovedDate(),

                orderTest.getAcceptedBy(),
                orderTest.getRejectedBy(),
                orderTest.getRejectedReason(),
                orderTest.getPatientArrivedNoteRad(),

                orderTest.getCancellationReason(),
                orderTest.getCancelledBy(),
                orderTest.getCancelledDate(),

                orderTest.getCreatedDate(),
                orderTest.getLastModifiedDate(),
                orderTest.getCreatedBy(),
                orderTest.getLastModifiedBy(),
                hasNote,

                orderTest.getUndoAcceptReason(),
                orderTest.getUndoAcceptBy(),
                orderTest.getUndoAcceptDate(),
                orderTest.getIcdDiagnosisId(),

                orderTest.getConfirmedBy(),
                orderTest.getConfirmedAt(),

                orderTest.getPrintNumber(),

                encounterId
        );
    }
}
