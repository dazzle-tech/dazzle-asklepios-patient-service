package com.dazzle.asklepios.web.rest.dto;

import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class PatientPrescriptionDTO {

    private Long id;

    private Long patientId;
    private Long encounterId;

    private Long prescriptionNum;
    private LocalDate prescriptionDate;

    private PrescriptionUrgencyLevel urgencyLevel;
    private PrescriptionStatus status;

    private Long fromFacilityId;
    private Long fromDepartmentId;
    private Long toFacilityId;
    private Long toDepartmentId;

    private String createdBy;
    private Instant createdDate;
    private String lastModifiedBy;
    private Instant lastModifiedDate;
}
