package com.dazzle.asklepios.service.dto.patientProcedure;

import com.dazzle.asklepios.domain.enumeration.Priority;
import com.dazzle.asklepios.domain.enumeration.ProcedureLevel;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

public record PatientProcedureCreateDTO(

        @NotNull(message = "procedure.required") Long procedureId,

        @NotNull Long patientId,
        @NotNull Long encounterId,

        @NotNull Long fromFacilityId,

        @NotNull(message = "facility.required") Long toFacilityId,

        @NotNull Long fromDepartmentId,
        @NotNull Long toDepartmentId,

        Long indicationId,

        @NotNull(message = "level.required") ProcedureLevel procedureLevel,

        @NotNull(message = "priority.required")
        Priority priority,

        @NotBlank(message = "bodyPart.required") String bodyPart,
        String side,

        @NotNull(message = "schedule.required")
        // @FutureOrPresent
        Instant scheduledDateTime,

        String notes,
        String extraDocumentation,
        String result,

        Boolean acceptUncoveredAsCash

) implements Serializable {}
