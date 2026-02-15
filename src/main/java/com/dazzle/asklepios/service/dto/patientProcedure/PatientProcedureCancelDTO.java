package com.dazzle.asklepios.service.dto.patientProcedure;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public record PatientProcedureCancelDTO(

        @NotBlank String cancellationReason,
        Long cancelledBy

) implements Serializable {}
