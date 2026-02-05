package com.dazzle.asklepios.service.vm;

import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientPrescriptionUpdateVM {
    public LocalDate prescriptionDate;
    public PrescriptionUrgencyLevel urgencyLevel;
    public Long toFacilityId;
    public Long toDepartmentId;

    public String lastModifiedBy;
}
