package com.dazzle.asklepios.service.dto.patientPrescription;

import java.math.BigDecimal;

public class PatientPrescriptionChronicCreateDTO {
    public Long patientId;
    public Long encounterId;
    public Long medicationsId;
    public Long activeIngredientId;
    public BigDecimal strength;
    public Boolean isActive;
}
