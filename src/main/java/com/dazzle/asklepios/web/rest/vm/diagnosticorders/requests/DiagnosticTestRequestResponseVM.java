package com.dazzle.asklepios.web.rest.vm.diagnosticorders.requests;

import com.dazzle.asklepios.domain.DiagnosticTestRequest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticTestRequestStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;

import java.time.Instant;

public record DiagnosticTestRequestResponseVM(
        Long id,
        DiagnosticTestRequestStatus status,
        TestType type,
        String name,
        String indication,

        Instant approvedDate,
        String approvedBy,

        Instant rejectedDate,
        String rejectedBy,
        String rejectedReason,

        Long fromDepartmentId,
        Long fromFacilityId,

        Instant createdDate,
        String createdBy,
        Instant lastModifiedDate,
        String lastModifiedBy
) {
    public static DiagnosticTestRequestResponseVM ofEntity(DiagnosticTestRequest e) {
        return new DiagnosticTestRequestResponseVM(
                e.getId(),
                e.getStatus(),
                e.getType(),
                e.getName(),
                e.getIndication(),

                e.getApprovedDate(),
                e.getApprovedBy(),

                e.getRejectedDate(),
                e.getRejectedBy(),
                e.getRejectedReason(),

                e.getFromDepartmentId(),
                e.getFromFacilityId(),

                e.getCreatedDate(),
                e.getCreatedBy(),
                e.getLastModifiedDate(),
                e.getLastModifiedBy()
        );
    }
}
