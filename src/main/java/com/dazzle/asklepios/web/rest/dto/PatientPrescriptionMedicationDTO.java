package com.dazzle.asklepios.web.rest.dto;

import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class PatientPrescriptionMedicationDTO {

    private Long id;
    private Long prescriptionHeaderId;

    private Long medicationsId;

    private PrescriptionInstructionsType instructionsType;
    private String instructions;

    private Long dose;
    private String doesUnit;
    private String rout;
    private String frequency;

    private Long duration;
    private String durationType;
    private Boolean chronicMedication;

    private Long maximumDose;
    private LocalDate validUtil;
    private Boolean allowedSubstitute;

    private String indicationManually;
    private String indicationUse;
    private String indicationIcd;
    private String parametersToMonitor;

    private Long numberOfRefills;
    private Integer refillValue;
    private String refillUnit;

    private String notes;
    private String extraDocumentation;

    private PrescriptionStatus status;

    private String createdBy;
    private Instant createdDate;
    private String lastModifiedBy;
    private Instant lastModifiedDate;
}
