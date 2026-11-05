package com.dazzle.asklepios.service.dto.patientProcedure;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public record PatientProcedureServiceEquipmentDTO(
        @NotNull Long serviceId
) implements Serializable {}
