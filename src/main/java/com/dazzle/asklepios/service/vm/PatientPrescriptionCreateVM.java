package com.dazzle.asklepios.service.vm;

import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientPrescriptionCreateVM {
    public Long patientId;
    public Long encounterId;
    public LocalDate prescriptionDate;
    public PrescriptionUrgencyLevel urgencyLevel;
    public Long fromFacilityId;
    public Long fromDepartmentId;
    public Long toFacilityId;
    public Long toDepartmentId;
}
