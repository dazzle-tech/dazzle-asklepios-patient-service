package com.dazzle.asklepios.service.dto.patientProcedure;

import com.dazzle.asklepios.domain.enumeration.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record PatientProcedureUpdateDTO(

        @NotNull Long id,

        Long procedureId,
        Long indicationId,

        String procedureLevel,
        Priority priority,

        String bodyPart,
        String side,

        Long toFacilityId,
        Long toDepartmentId,

        @NotNull
        @FutureOrPresent
        Instant scheduledDateTime,
        String notes,
        String extraDocumentation

) implements Serializable {}
