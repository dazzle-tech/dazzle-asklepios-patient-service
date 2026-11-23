package com.dazzle.asklepios.web.rest.vm.patientPackage;

import com.dazzle.asklepios.domain.enumeration.Priority;
import com.dazzle.asklepios.domain.enumeration.ProcedureLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

/**
 * Order one item from a patient package in a visit. Only the fields the item type needs are read:
 * laboratory / radiology / pathology use isUrgent, reason, notes, icdDiagnosisId;
 * procedure needs procedureLevel, priority, bodyPart, scheduledDateTime (indicationId, side, departments optional);
 * service uses quantity and notes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPackageOrderVM(
        @NotNull Long encounterId,
        @Min(1) Integer quantity,
        Boolean isUrgent,
        @Size(max = 1000) String reason,
        @Size(max = 2000) String notes,
        Long icdDiagnosisId,
        Boolean acceptUncoveredAsCash,
        /** Procedure indication (patient diagnosis), as on the procedure order screen. */
        Long indicationId,
        ProcedureLevel procedureLevel,
        Priority priority,
        @Size(max = 255) String bodyPart,
        @Size(max = 255) String side,
        Instant scheduledDateTime,
        Long toFacilityId,
        Long toDepartmentId
) implements Serializable {}
