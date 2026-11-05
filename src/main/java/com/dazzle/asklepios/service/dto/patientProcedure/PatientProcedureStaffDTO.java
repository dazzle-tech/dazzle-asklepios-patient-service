package com.dazzle.asklepios.service.dto.patientProcedure;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public record PatientProcedureStaffDTO(
        @NotNull Long userId,
        String responsibility,
        Boolean isPresent
) implements Serializable {}
