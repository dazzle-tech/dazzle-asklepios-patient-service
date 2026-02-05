package com.dazzle.asklepios.service.vm;

import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PrescriptionMedicationUpdateVM {

    public PrescriptionInstructionsType instructionsType;
    public String instructions;

    public Long dose;
    public String doesUnit;
    public String rout;
    public String frequency;

    public Boolean chronicMedication;
    public Long duration;
    public String durationType;

    public Long maximumDose;
    public LocalDate validUtil;
    public Boolean allowedSubstitute;

    public String indicationManually;
    public String indicationUse;
    public String indicationIcd;
    public String parametersToMonitor;

    public Long numberOfRefills;
    public Integer refillValue;
    public String refillUnit;

    public String notes;
    public String extraDocumentation;

    public String lastModifiedBy;
}
