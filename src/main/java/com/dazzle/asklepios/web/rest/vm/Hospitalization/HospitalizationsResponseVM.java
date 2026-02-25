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
        String createdBy,
        Instant createdDate
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
                entity.getCreatedBy(),
                entity.getCreatedDate()
        );
    }
}
