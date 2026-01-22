package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.TelephonicConsultation;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import java.io.Serializable;
import java.util.Date;

public record TelephonicConsultationResponseVM(

        Long id,

        Long patientId,
        Long encounterId,
        Long practitionerId,

        Date dateOfCall,
        String consultationContent,

        Integer approvalNumber,
        String notes,
        String extraDocumentation,

        DiagnosticStatus status,

        String cancellationReason,
        Date cancelledAt,
        Long cancelledBy,

        java.time.Instant createdDate,
        String createdBy

) implements Serializable {

    public static TelephonicConsultationResponseVM ofEntity(TelephonicConsultation entity) {
        return new TelephonicConsultationResponseVM(
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getEncounterId(),
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
