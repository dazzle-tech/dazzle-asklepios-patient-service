package com.dazzle.asklepios.service.dto.patientPrescription;

import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class PrescriptionMedicationCreateDTO {

    @NotNull public Long prescriptionHeaderId;
    @NotNull public Long medicationsId;
    @NotNull public PrescriptionInstructionsType instructionsType;
    public String instructions;
    public Long dose;
    public String doesUnit;
    public String rout;
    public String frequency;
    public Long  activeIngredientId;
    public Boolean chronicMedication;
    public Long duration;
    public String durationType;

    public Long maximumDose;
    public Boolean allowedSubstitute;

    public String indicationManually;
    public String indicationUse;
    public Long indicationIcd;
    public String parametersToMonitor;
    public String administrationInstructions;
    public Long numberOfRefills;
    public Integer refillValue;
    public String refillUnit;

    public String notes;
    public String extraDocumentation;

    public String createdBy;
}