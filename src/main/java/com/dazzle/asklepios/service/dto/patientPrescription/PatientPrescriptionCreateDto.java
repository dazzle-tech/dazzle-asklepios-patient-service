package com.dazzle.asklepios.service.dto.patientPrescription;

import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class PatientPrescriptionCreateDto {
    public Long patientId;
    public Long encounterId;
    public LocalDate prescriptionDate;
    public PrescriptionUrgencyLevel urgencyLevel;
    public Long fromFacilityId;
    public Long fromDepartmentId;
    public Long toFacilityId;
    public Long toDepartmentId;
}
