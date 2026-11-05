package com.dazzle.asklepios.service.dto.patientProcedure;

import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record PatientProcedureStatusDTO(

        @NotNull ProcStatus status

) implements Serializable {}
