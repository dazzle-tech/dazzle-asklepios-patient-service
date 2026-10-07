package com.dazzle.asklepios.service.dto.appointmentRequest;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.RecurrenceUnit;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecurringAppointmentRequestDTO(
        @NotNull Long patientId,
        @NotNull Long facilityId,
        @NotNull Long departmentId,
        @NotNull Long sourceEncounterId,
        @NotNull TemplateType requestedResourceType,
        @NotNull Long requestedResourceId,
        EncounterPriority priority,
        @Size(max = 255) String reason,
        String note,
        @NotEmpty List<@NotNull DayOfWeek> daysOfWeek,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull @Min(1) @Max(52) Integer period,
        @NotNull RecurrenceUnit periodUnit,
        List<@NotNull Long> selectedAppointmentIds
) {
}
