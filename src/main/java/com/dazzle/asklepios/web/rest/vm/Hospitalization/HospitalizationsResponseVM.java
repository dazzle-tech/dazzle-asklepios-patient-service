package com.dazzle.asklepios.web.rest.vm.Hospitalization;

import com.dazzle.asklepios.domain.Hospitalization;

import java.time.Instant;
import java.util.Date;

public record HospitalizationsResponseVM(
        Long id,
        Long patientId,
        String facility,
        String reason,
        String admissionType,
        Date dateOfAdmission,
        Integer lengthOfStayDays,
        String outcomes,
        String medicalInterventionsPerformed,

        // Cancel fields
        String status,
        String cancelledBy,
        Date cancelledDate,
        String cancellationReason,

        // Audit fields
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static HospitalizationsResponseVM ofEntity(Hospitalization entity) {
        return new HospitalizationsResponseVM(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getFacility(),
                entity.getReason(),
                entity.getAdmissionType(),
                entity.getDateOfAdmission(),
                entity.getLengthOfStayDays(),
                entity.getOutcomes(),
                entity.getMedicalInterventionsPerformed(),

                // Cancel fields
                entity.getStatus(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason(),

                // Audit fields
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}