package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.TelephonicConsultation;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
public record TelephonicConsultationResponseVM(

        Long id,
        Long patientId,
        Long encounterId,
        Long practitionerId,

        Instant dateOfCall,
        String consultationContent,

        Long approvalNumber,
        String notes,
        String extraDocumentation,

        DiagnosticStatus status,

        String cancellationReason,
        Instant cancelledAt,
        String cancelledBy,

        Instant createdDate,
        String createdBy

) implements Serializable {

    public static TelephonicConsultationResponseVM ofEntity(TelephonicConsultation entity) {
        return new TelephonicConsultationResponseVM(
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getEncounter() != null ? entity.getEncounter().getId() : null,
                entity.getPractitionerId(),
                entity.getDateOfCall(),
                entity.getConsultationContent(),
                entity.getApprovalNumber(),
                entity.getNotes(),
                entity.getExtraDocumentation(),
                entity.getStatus(),
                entity.getCancellationReason(),
                entity.getCancelledAt(),
                entity.getCancelledBy(),
                entity.getCreatedDate(),
                entity.getCreatedBy()
        );
    }
}
