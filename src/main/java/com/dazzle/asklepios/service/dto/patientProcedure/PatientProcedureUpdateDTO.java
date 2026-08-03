package com.dazzle.asklepios.service.dto.patientProcedure;

import com.dazzle.asklepios.domain.enumeration.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record PatientProcedureUpdateDTO(

        @NotNull Long id,

        @NotNull Long procedureId,
        Long indicationId,

        @NotNull String procedureLevel,
        @NotNull Priority priority,

        @NotNull String bodyPart,
        String side,

        @NotNull Long toFacilityId,
        @NotNull Long toDepartmentId,

        @NotNull
//        @FutureOrPresent
        Instant scheduledDateTime,
        String notes,
        String extraDocumentation,
        String result

) implements Serializable {}
