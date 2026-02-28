package com.dazzle.asklepios.service.dto.patientPrescription;

import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class PatientPrescriptionCreateDto {
    @NotNull public Long patientId;
    @NotNull public Long encounterId;
    public LocalDate prescriptionDate;
    public PrescriptionUrgencyLevel urgencyLevel;
    public Long fromFacilityId;
    public Long fromDepartmentId;
    public Long toFacilityId;
    public Long toDepartmentId;
}
