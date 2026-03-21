package com.dazzle.asklepios.service.dto.patientPrescription;

import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class PatientPrescriptionUpdateDTO {
    public LocalDate prescriptionDate;
    public PrescriptionUrgencyLevel urgencyLevel;
    public Long toFacilityId;
    public Long toDepartmentId;

    public String lastModifiedBy;
}